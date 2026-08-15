package nexa.plugin.mqtt.manager;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * Registry and connection pool for shared MQTT clients.
 *
 * A single physical MqttClient may be shared by multiple Nexa input nodes.
 * Paho must therefore have exactly one listener per topic from this manager,
 * while the manager fans each received message out to all registered node listeners.
 */
public final class MqttBrokerManager {
    private static final ConcurrentHashMap<String, MqttClient> clientPool = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, MqttClient> lookupRegistry = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, SharedSubscription> subscriptions = new ConcurrentHashMap<>();
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
