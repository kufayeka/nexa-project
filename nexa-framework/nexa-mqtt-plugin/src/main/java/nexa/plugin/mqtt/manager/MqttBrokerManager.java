package nexa.plugin.mqtt.manager;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * Registry and connection pool for shared MQTT clients.
 *
 * Outbound publishing is deliberately split into two stages:
 * 1. Nexa execution threads only enqueue into a bounded queue.
 * 2. Publisher workers submit messages using Paho's non-blocking MqttTopic API.
 *
 * A per-client semaphore limits the number of outstanding deliveries so the
 * queue cannot outrun Paho's in-flight window indefinitely.
 */
public final class MqttBrokerManager {
    private static final int DEFAULT_QUEUE_CAPACITY = 8192;
    private static final String DEFAULT_OVERFLOW_STRATEGY = "BLOCK";
    private static final int DEFAULT_WORKER_THREADS = 2;
    private static final int DEFAULT_MAX_INFLIGHT = 5000;
    private static final long DEFAULT_ENQUEUE_TIMEOUT_MS = 5L;

    private static final ConcurrentHashMap<String, MqttClient> clientPool = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, MqttClient> lookupRegistry = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, SharedSubscription> subscriptions = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, PublishWorker> publishWorkers = new ConcurrentHashMap<>();
    private static final ReentrantLock lock = new ReentrantLock();

    private MqttBrokerManager() {
    }

    public static MqttClient getOrCreateClient(String brokerUrl, int keepAlive) throws Exception {
        return getOrCreateClient(brokerUrl, keepAlive, PublishConfig.defaults());
    }

    public static MqttClient getOrCreateClient(
            String brokerUrl,
            int keepAlive,
            PublishConfig publishConfig) throws Exception {
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
                    options.setMaxInflight(publishConfig.maxInflight());

                    client.connect(options);
                    clientPool.put(brokerUrl, client);
                    configurePublishWorker(client, publishConfig);
                    System.out.println("[MQTT Pool] TCP Connection established to: " + brokerUrl);
                } else {
                    client = existingClient;
                    configurePublishWorker(client, publishConfig);
                }
            } finally {
                lock.unlock();
            }
        } else {
            configurePublishWorker(client, publishConfig);
        }
        return client;
    }

    /**
     * Enqueues an outbound MQTT message. No network operation is performed on
     * the Nexa flow execution thread.
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
                ignored -> new PublishWorker(client, PublishConfig.defaults()));

        boolean accepted = worker.enqueue(new PublishRequest(topic, copyMessage(message)));
        if (!accepted && worker.config.enabled()) {
            System.err.println("[MQTT Pool] Publish queue full, dropping message for topic: " + topic);
        }
        return accepted;
    }

    private static void configurePublishWorker(MqttClient client, PublishConfig config) {
        publishWorkers.compute(client.getClientId(), (key, existing) -> {
            if (existing == null) {
                return new PublishWorker(client, config);
            }
            existing.updateConfig(config);
            return existing;
        });
    }

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
                subscriptions.entrySet().removeIf(entry -> entry.getValue().client == client);
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
        return copy;
    }

    private static void stopPublishWorker(MqttClient client, boolean drain) {
        PublishWorker worker = publishWorkers.remove(client.getClientId());
        if (worker != null) {
            worker.shutdown(drain);
        }
    }

    public static final class PublishConfig {
        private final boolean enabled;
        private final int capacity;
        private final String overflowStrategy;
        private final int workerThreads;
        private final int maxInflight;

        public PublishConfig(
                boolean enabled,
                int capacity,
                String overflowStrategy,
                int workerThreads,
                int maxInflight) {
            this.enabled = enabled;
            this.capacity = Math.max(1, capacity);
            this.overflowStrategy = normalizeOverflowStrategy(overflowStrategy);
            this.workerThreads = Math.max(1, workerThreads);
            this.maxInflight = Math.max(1, maxInflight);
        }

        public static PublishConfig defaults() {
            return new PublishConfig(
                    true,
                    DEFAULT_QUEUE_CAPACITY,
                    DEFAULT_OVERFLOW_STRATEGY,
                    DEFAULT_WORKER_THREADS,
                    DEFAULT_MAX_INFLIGHT);
        }

        /**
         * Expected resource configuration:
         * {
         *   "publish": {
         *     "queue": {
         *       "enabled": true,
         *       "capacity": 50000,
         *       "overflowStrategy": "BLOCK"
         *     },
         *     "worker": {
         *       "threads": 2,
         *       "maxInflight": 5000
         *     }
         *   }
         * }
         */
        public static PublishConfig fromConfig(Map<String, Object> config) {
            Object publishValue = config == null ? null : config.get("publish");
            if (!(publishValue instanceof Map<?, ?> publish)) {
                return defaults();
            }

            boolean enabled = true;
            int capacity = DEFAULT_QUEUE_CAPACITY;
            String overflowStrategy = DEFAULT_OVERFLOW_STRATEGY;
            int workerThreads = DEFAULT_WORKER_THREADS;
            int maxInflight = DEFAULT_MAX_INFLIGHT;

            Object queueValue = publish.get("queue");
            if (queueValue instanceof Map<?, ?> queue) {
                enabled = booleanValue(queue.get("enabled"), enabled);
                capacity = intValue(queue.get("capacity"), capacity);
                overflowStrategy = stringValue(queue.get("overflowStrategy"), overflowStrategy);
            }

            Object workerValue = publish.get("worker");
            if (workerValue instanceof Map<?, ?> worker) {
                workerThreads = intValue(worker.get("threads"), workerThreads);
                maxInflight = intValue(worker.get("maxInflight"), maxInflight);
            }

            return new PublishConfig(enabled, capacity, overflowStrategy, workerThreads, maxInflight);
        }

        public boolean enabled() { return enabled; }
        public int capacity() { return capacity; }
        public String overflowStrategy() { return overflowStrategy; }
        public int workerThreads() { return workerThreads; }
        public int maxInflight() { return maxInflight; }

        private static String normalizeOverflowStrategy(String value) {
            if (value == null) return DEFAULT_OVERFLOW_STRATEGY;
            String normalized = value.trim().toUpperCase();
            return switch (normalized) {
                case "BLOCK", "DROP_NEWEST", "DROP_OLDEST" -> normalized;
                default -> DEFAULT_OVERFLOW_STRATEGY;
            };
        }

        private static boolean booleanValue(Object value, boolean fallback) {
            return value instanceof Boolean b ? b : fallback;
        }

        private static int intValue(Object value, int fallback) {
            return value instanceof Number n ? n.intValue() : fallback;
        }

        private static String stringValue(Object value, String fallback) {
            return value instanceof String s && !s.isBlank() ? s : fallback;
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
        private final ArrayBlockingQueue<PublishRequest> queue;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final Semaphore inflight;
        private volatile PublishConfig config;
        private volatile Thread[] threads;

        private PublishWorker(MqttClient client, PublishConfig config) {
            this.client = client;
            this.config = config;
            this.queue = new ArrayBlockingQueue<>(config.capacity());
            this.inflight = new Semaphore(config.maxInflight());
            installDeliveryCallback();
            startThreads(config.workerThreads());
        }

        private void installDeliveryCallback() {
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    if (cause != null && running.get()) {
                        System.err.println("[MQTT Pool] Connection lost: " + cause.getMessage());
                    }
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // Inbound traffic is handled through shared subscriptions.
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    inflight.release();
                }
            });
        }

        private void startThreads(int count) {
            Thread[] created = new Thread[count];
            for (int i = 0; i < count; i++) {
                Thread thread = new Thread(
                        this::run,
                        "Nexa-MQTT-Publisher-" + client.getClientId() + "-" + (i + 1));
                thread.setDaemon(true);
                thread.start();
                created[i] = thread;
            }
            this.threads = created;
        }

        private void updateConfig(PublishConfig nextConfig) {
            if (nextConfig == null || !running.get()) return;
            this.config = nextConfig;
        }

        private boolean enqueue(PublishRequest request) {
            if (!running.get() || !config.enabled()) return false;

            try {
                return switch (config.overflowStrategy()) {
                    case "DROP_OLDEST" -> {
                        if (queue.offer(request)) {
                            yield true;
                        }
                        queue.poll();
                        yield queue.offer(request);
                    }
                    case "DROP_NEWEST" -> queue.offer(request);
                    case "BLOCK" -> queue.offer(request, DEFAULT_ENQUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    default -> queue.offer(request);
                };
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

                    inflight.acquire();
                    boolean submitted = false;
                    try {
                        if (!client.isConnected()) {
                            continue;
                        }

                        // MqttTopic.publish is the non-blocking publish API of the
                        // classic MqttClient. It returns after Paho accepts the
                        // message and completes delivery in the background.
                        client.getTopic(request.topic).publish(request.message)
                                .setActionCallback(new org.eclipse.paho.client.mqttv3.IMqttActionListener() {
                                    @Override
                                    public void onSuccess(org.eclipse.paho.client.mqttv3.IMqttToken asyncActionToken) {
                                        // deliveryComplete releases the permit.
                                    }

                                    @Override
                                    public void onFailure(
                                            org.eclipse.paho.client.mqttv3.IMqttToken asyncActionToken,
                                            Throwable exception) {
                                        inflight.release();
                                        if (running.get() && exception != null) {
                                            System.err.println("[MQTT Pool] Async publish error: " + exception.getMessage());
                                        }
                                    }
                                });
                        submitted = true;
                    } finally {
                        if (!submitted) {
                            inflight.release();
                        }
                    }
                } catch (InterruptedException e) {
                    if (!running.get()) break;
                } catch (Exception e) {
                    if (running.get()) {
                        System.err.println("[MQTT Pool] Async publish worker error: " + e.getMessage());
                    }
                }
            }
        }

        private void shutdown(boolean drain) {
            if (!running.compareAndSet(true, false)) return;
            if (!drain) queue.clear();

            Thread[] currentThreads = threads;
            for (Thread thread : currentThreads) {
                thread.interrupt();
            }

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            for (Thread thread : currentThreads) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try {
                    thread.join(TimeUnit.NANOSECONDS.toMillis(remaining));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!queue.isEmpty()) queue.clear();
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
