package nexa.plugin.mqtt.manager;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * Registry and connection pool for shared MQTT clients.
 *
 * A single physical MqttClient may be shared by multiple Nexa input nodes.
 * Paho must therefore have exactly one listener per topic from this manager,
 * while the manager fans each received message out to all registered node listeners.
 *
 * Outbound messages are queued per physical MQTT client. Flow execution threads
 * never call Paho publish directly, preventing a slow broker or a full Paho
 * inflight window from blocking/failing the execution pipeline.
 */
public final class MqttBrokerManager {
    private static final int PUBLISH_QUEUE_CAPACITY = 8192;
    private static final long PUBLISH_ENQUEUE_TIMEOUT_MS = 5L;

    private static final ConcurrentHashMap<String, MqttClient> clientPool = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, MqttClient> lookupRegistry = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, SharedSubscription> subscriptions = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, PublishWorker> publishWorkers = new ConcurrentHashMap<>();
    private static final ReentrantLock lock = new ReentrantLock();

    private MqttBrokerManager() {
    }

    public static MqttClient getOrCreateClient(String brokerUrl, int keepAlive) throws Exception {
        MqttClient client = clientPool.get(brokerUrl);

        if (client == null || !client.isConnected()) {
            lock.lock();
            try {
                MqttClient existingClient = clientPool.get(brokerUrl);
                if (existingClient == null || !existingClient.isConnected()) {
                    if (existingClient != null) {
                        stopPublishWorker(existingClient, true);
                        try {
                            existingClient.close();
                        } catch (Exception ignored) {
                        }
                    }

                    String clientId = "Nexa-Shared-" + MqttClient.generateClientId();
                    client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setKeepAliveInterval(keepAlive);
                    options.setCleanSession(true);
                    options.setAutomaticReconnect(true);
                    options.setMaxInflight(500);

                    client.connect(options);
                    clientPool.put(brokerUrl, client);
                    System.out.println("[MQTT Pool] TCP Connection established to: " + brokerUrl);
                } else {
                    client = existingClient;
                }
            } finally {
                lock.unlock();
            }
        }
        return client;
    }

    /**
     * Enqueues an outbound MQTT message for the shared client.
     * This method is intentionally non-blocking from the flow execution point
     * of view; only a very small bounded offer is used for backpressure.
     *
     * @return true when the message was accepted by the queue, false when the
     *         queue is saturated or the client is unavailable
     */
    public static boolean publish(MqttClient client, String topic, MqttMessage message) {
        if (client == null || topic == null || topic.isBlank() || message == null) {
            return false;
        }
        if (!client.isConnected()) {
            return false;
        }

        PublishWorker worker = publishWorkers.computeIfAbsent(
                client.getClientId(),
                ignored -> new PublishWorker(client));

        boolean accepted = worker.enqueue(new PublishRequest(topic, copyMessage(message)));
        if (!accepted) {
            System.err.println("[MQTT Pool] Publish queue full, dropping message for topic: " + topic);
        }
        return accepted;
    }

    /**
     * Registers a logical node listener on a shared MQTT topic.
     * Only the first listener causes a real broker subscription. All later
     * listeners are dispatched by the shared listener installed here.
     */
    public static void subscribe(
            MqttClient client,
            String topic,
            String subscriberId,
            BiConsumer<String, MqttMessage> listener) throws Exception {
        if (client == null) throw new IllegalArgumentException("client must not be null");
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic must not be blank");
        if (subscriberId == null || subscriberId.isBlank()) throw new IllegalArgumentException("subscriberId must not be blank");
        if (listener == null) throw new IllegalArgumentException("listener must not be null");

        String key = subscriptionKey(client, topic);
        lock.lock();
        try {
            SharedSubscription subscription = subscriptions.get(key);
            if (subscription == null) {
                subscription = new SharedSubscription(client, topic);
                subscriptions.put(key, subscription);
                SharedSubscription created = subscription;
                IMqttMessageListener sharedListener = (receivedTopic, message) -> created.dispatch(receivedTopic, message);
                subscription.setBrokerListener(sharedListener);
                client.subscribe(topic, 1, sharedListener);
                System.out.println("[MQTT Pool] Subscribed shared topic: " + topic + " | ClientID: " + client.getClientId());
            }
            subscription.listeners.put(subscriberId, listener);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes one logical node listener. The broker subscription remains while
     * at least one node is still listening to the topic.
     */
    public static void unsubscribe(MqttClient client, String topic, String subscriberId) {
        if (client == null || topic == null || subscriberId == null) return;

        String key = subscriptionKey(client, topic);
        lock.lock();
        try {
            SharedSubscription subscription = subscriptions.get(key);
            if (subscription == null) return;

            subscription.listeners.remove(subscriberId);
            if (!subscription.listeners.isEmpty()) return;

            subscriptions.remove(key, subscription);
            try {
                if (client.isConnected()) client.unsubscribe(topic);
            } catch (Exception ignored) {
            }
        } finally {
            lock.unlock();
        }
    }

    public static void removeClient(String brokerUrl) {
        lock.lock();
        try {
            MqttClient client = clientPool.remove(brokerUrl);
            if (client != null) {
                subscriptions.entrySet().removeIf(entry -> {
                    SharedSubscription subscription = entry.getValue();
                    return subscription.client == client;
                });

                stopPublishWorker(client, true);

                try {
                    if (client.isConnected()) client.disconnect();
                } catch (Exception ignored) {
                }
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        } finally {
            lock.unlock();
        }
    }

    public static void registerClientReference(String targetId, String name, MqttClient client) {
        if (targetId != null) lookupRegistry.put(targetId, client);
        if (name != null) lookupRegistry.put(name, client);
    }

    public static void unregisterClientReference(String targetId, String name) {
        if (targetId != null) lookupRegistry.remove(targetId);
        if (name != null) lookupRegistry.remove(name);
    }

    public static MqttClient getClientByNameOrId(String nameOrId) {
        if (nameOrId == null) return null;
        return lookupRegistry.get(nameOrId);
    }

    private static String subscriptionKey(MqttClient client, String topic) {
        return client.getClientId() + "\u0000" + topic;
    }

    private static MqttMessage copyMessage(MqttMessage source) {
        MqttMessage copy = new MqttMessage(source.getPayload().clone());
        copy.setQos(source.getQos());
        copy.setRetained(source.isRetained());
        copy.setDup(source.isDuplicate());
        return copy;
    }

    private static void stopPublishWorker(MqttClient client, boolean drain) {
        PublishWorker worker = publishWorkers.remove(client.getClientId());
        if (worker != null) {
            worker.shutdown(drain);
        }
    }

    private static final class PublishRequest {
        private final String topic;
        private final MqttMessage message;

        private PublishRequest(String topic, MqttMessage message) {
            this.topic = topic;
            this.message = message;
        }
    }

    private static final class PublishWorker {
        private final MqttClient client;
        private final ArrayBlockingQueue<PublishRequest> queue = new ArrayBlockingQueue<>(PUBLISH_QUEUE_CAPACITY);
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final Thread thread;

        private PublishWorker(MqttClient client) {
            this.client = client;
            this.thread = new Thread(this::run, "Nexa-MQTT-Publisher-" + client.getClientId());
            this.thread.setDaemon(true);
            this.thread.start();
        }

        private boolean enqueue(PublishRequest request) {
            if (!running.get()) return false;
            try {
                return queue.offer(request, PUBLISH_ENQUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private void run() {
            while (running.get() || !queue.isEmpty()) {
                try {
                    PublishRequest request = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (request == null) continue;
                    if (!client.isConnected()) continue;
                    client.publish(request.topic, request.message);
                } catch (InterruptedException e) {
                    if (!running.get()) break;
                } catch (Exception e) {
                    if (running.get() && client.isConnected()) {
                        System.err.println("[MQTT Pool] Publish worker error: " + e.getMessage());
                    }
                }
            }
        }

        private void shutdown(boolean drain) {
            if (!running.compareAndSet(true, false)) return;
            if (!drain) queue.clear();
            thread.interrupt();
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (thread.isAlive()) {
                queue.clear();
            }
        }
    }

    private static final class SharedSubscription {
        private final MqttClient client;
        private final String topic;
        private final ConcurrentMap<String, BiConsumer<String, MqttMessage>> listeners = new ConcurrentHashMap<>();
        @SuppressWarnings("unused")
        private IMqttMessageListener brokerListener;

        private SharedSubscription(MqttClient client, String topic) {
            this.client = client;
            this.topic = topic;
        }

        private void setBrokerListener(IMqttMessageListener brokerListener) {
            this.brokerListener = brokerListener;
        }

        private void dispatch(String receivedTopic, MqttMessage message) {
            for (BiConsumer<String, MqttMessage> listener : listeners.values()) {
                try {
                    listener.accept(receivedTopic, message);
                } catch (Throwable throwable) {
                    System.err.println("[MQTT Pool] Shared listener error for topic " + topic + ": " + throwable.getMessage());
                }
            }
        }
    }
}
