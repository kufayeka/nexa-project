package nexa.plugin.mqtt.manager;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MqttBrokerManager bertindak sebagai registry dan connection pool untuk MqttClient.
 * Kelas ini thread-safe dan menggunakan ReentrantLock untuk menghindari locking carrier thread
 * ketika dijalankan di atas Virtual Threads (mencegah thread pinning).
 */
public final class MqttBrokerManager {
    // Menyimpan koneksi fisik MQTT berdasarkan broker URL
    private static final ConcurrentHashMap<String, MqttClient> clientPool = new ConcurrentHashMap<>();
    
    // Menyimpan referensi pencarian MQTT Client berdasarkan ID resource atau nama pool
    private static final ConcurrentHashMap<String, MqttClient> lookupRegistry = new ConcurrentHashMap<>();
    
    private static final ReentrantLock lock = new ReentrantLock();

    /**
     * Mendapatkan koneksi MqttClient yang sudah ada atau membuat koneksi baru jika belum tersedia.
     */
    public static MqttClient getOrCreateClient(String brokerUrl, int keepAlive) throws Exception {
        MqttClient client = clientPool.get(brokerUrl);

        if (client == null || !client.isConnected()) {
            lock.lock();
            try {
                MqttClient existingClient = clientPool.get(brokerUrl);
                if (existingClient == null || !existingClient.isConnected()) {
                    // Close the old client to release resources if it exists
                    if (existingClient != null) {
                        try {
                            existingClient.close();
                        } catch (Exception ignored) {}
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
     * Menghapus koneksi client fisik dari pool dan menutup koneksi MQTT secara aman.
     */
    public static void removeClient(String brokerUrl) {
        lock.lock();
        try {
            MqttClient client = clientPool.remove(brokerUrl);
            if (client != null) {
                try {
                    if (client.isConnected()) {
                        client.disconnect();
                    }
                } catch (Exception ignored) {}
                try {
                    client.close();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
        } finally {
            lock.unlock();
        }
    }

    /**
     * Meregistrasikan pemetaan referensi client agar bisa dicari menggunakan ID resource maupun Nama Pool.
     */
    public static void registerClientReference(String targetId, String name, MqttClient client) {
        if (targetId != null) {
            lookupRegistry.put(targetId, client);
        }
        if (name != null) {
            lookupRegistry.put(name, client);
        }
    }

    /**
     * Menghapus pemetaan referensi client untuk mencegah kebocoran memori (leak) setelah undeploy.
     */
    public static void unregisterClientReference(String targetId, String name) {
        if (targetId != null) {
            lookupRegistry.remove(targetId);
        }
        if (name != null) {
            lookupRegistry.remove(name);
        }
    }

    /**
     * Mengambil instance MqttClient yang aktif berdasarkan nama pool atau ID resource yang di-refer.
     */
    public static MqttClient getClientByNameOrId(String nameOrId) {
        if (nameOrId == null) {
            return null;
        }
        return lookupRegistry.get(nameOrId);
    }
}

