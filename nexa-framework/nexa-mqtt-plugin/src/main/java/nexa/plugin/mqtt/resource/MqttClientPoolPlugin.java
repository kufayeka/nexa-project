package nexa.plugin.mqtt.resource;

import nexa.framework.runtime.api.plugin.NexaResourcePlugin;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.plugin.mqtt.manager.MqttBrokerManager;
import org.eclipse.paho.client.mqttv3.MqttClient;
import java.util.Map;

/**
 * MqttClientPoolPlugin mengelola siklus hidup koneksi MqttClient global (shared resource).
 * Didaftarkan sebagai resource plugin bertipe "mqtt-client-pool".
 */
public final class MqttClientPoolPlugin implements NexaResourcePlugin {
    private String targetId;
    private String name;
    private String brokerUrl;
    private int keepAlive;
    private MqttClient mqttClient;

    @Override
    public String getPluginType() {
        return "mqtt-client-pool";
    }

    @Override
    public Object getNativeClient() {
        return this.mqttClient;
    }

    @Override
    public void onInit(final String targetId, final Map<String, Object> config, final NexaPluginContext context) throws Exception {
        this.targetId = targetId;
        // Ambil nama koneksi dari config, jika tidak ada fallback ke targetId
        this.name = (String) config.get("name");
        this.brokerUrl = (String) config.getOrDefault("brokerUrl", "tcp://localhost:1883");
        this.keepAlive = ((Number) config.getOrDefault("keepAlive", 60)).intValue();
    }

    @Override
    public void onStart() throws Exception {
        // Alur Kerja Inisialisasi:
        // 1. Dapatkan atau buat instance MqttClient melalui MqttBrokerManager secara thread-safe.
        // 2. Daftarkan referensi client ini agar bisa dicari oleh node plugin menggunakan nama/ID.
        this.mqttClient = MqttBrokerManager.getOrCreateClient(this.brokerUrl, this.keepAlive);
        MqttBrokerManager.registerClientReference(this.targetId, this.name, this.mqttClient);
    }

    @Override
    public void onStop() {
        // Alur Kerja Deaktivasi:
        // 1. Hapus pemetaan referensi client dari lookup registry lokal untuk mencegah memory leak.
        // 2. Tutup koneksi fisik MQTT client pool dan lepaskan socket connection.
        MqttBrokerManager.unregisterClientReference(this.targetId, this.name);
        MqttBrokerManager.removeClient(this.brokerUrl);
    }
}
