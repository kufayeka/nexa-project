package nexa.plugin.mqtt.node;

import nexa.framework.runtime.api.plugin.NexaSourcePlugin;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.plugin.mqtt.manager.MqttBrokerManager;
import org.eclipse.paho.client.mqttv3.MqttClient;
import java.util.Map;
import java.util.function.Consumer;

public final class MqttSharedInputPlugin implements NexaSourcePlugin {
    private Consumer<RuntimeMessage> emitter;
    private MqttClient mqttClient;
    private String mqttClientPool;
    private String topic;

    @Override
    public String getPluginType() {
        return "mqtt-shared-input"; 
    }

    @Override
    public void setEmitter(Consumer<RuntimeMessage> emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onInit(final String targetId, final Map<String, Object> config, final NexaPluginContext context) throws Exception {
        this.mqttClientPool = (String) config.get("mqttClientPool");
        this.topic = (String) config.getOrDefault("topic", "sensor/data");

        // Alur Kerja Resolusi Client:
        // 1. Coba cari client dari NexaPluginContext menggunakan ID resource
        Object clientObj = context.getSharedResource(this.mqttClientPool);
        if (clientObj instanceof MqttClient client) {
            this.mqttClient = client;
        } else {
            // 2. Jika tidak ditemukan (misal di-refer menggunakan nama pool), cari dari registry lokal
            this.mqttClient = MqttBrokerManager.getClientByNameOrId(this.mqttClientPool);
        }
    }

    @Override
    public void onStart() throws Exception {
        if (this.mqttClient == null) {
            throw new IllegalStateException("Mqtt Client Pool tidak ditemukan atau belum terinisialisasi: " + this.mqttClientPool);
        }

        // Jalankan subscription topik MQTT menggunakan client pool yang dipinjam
        this.mqttClient.subscribe(this.topic, (receivedTopic, mqttMessage) -> {
            RuntimeMessage nexaMsg = new RuntimeMessage();
            nexaMsg.writeValue("payload.rawData", new String(mqttMessage.getPayload()));
            nexaMsg.writeValue("payload.topic", receivedTopic);
            
            if (this.emitter != null) {
                this.emitter.accept(nexaMsg); 
            }
        });
    }

    @Override
    public void onStop() {
        try {
            // Unsubscribe topik dari broker saat undeploy/stop, koneksi fisik tetap hidup di level resource
            if (this.mqttClient != null && this.mqttClient.isConnected()) {
                this.mqttClient.unsubscribe(this.topic);
            }
        } catch (Exception ignored) {}
    }
}
