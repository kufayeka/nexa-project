package nexa.plugin.mqtt.node;

import nexa.framework.runtime.api.plugin.NexaSinkPlugin;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.plugin.mqtt.manager.MqttBrokerManager;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import java.util.Map;

public final class MqttSharedSinkPlugin implements NexaSinkPlugin {
    private MqttClient mqttClient;
    private String mqttClientPool;
    private String topic;

    @Override
    public String getPluginType() {
        return "mqtt-shared-sink";
    }

    @Override
    public void onInit(final String targetId, final Map<String, Object> config, final NexaPluginContext context)
            throws Exception {
        this.mqttClientPool = (String) config.get("mqttClientPool");
        this.topic = (String) config.getOrDefault(
                "topic",
                "sensor/processed");
    }

    @Override
    public void onStart() throws Exception {
        this.mqttClient = MqttBrokerManager.getClientByNameOrId(this.mqttClientPool);

        if (this.mqttClient == null) {
            throw new IllegalStateException(
                    "Mqtt Client Pool tidak ditemukan atau belum terinisialisasi: "
                            + this.mqttClientPool);
        }
    }

    @Override
    public void consume(RuntimeMessage msg) {
        try {
            Object rawPayload = msg.readRawValue("payload");
            if (rawPayload == null)
                return;

            MqttMessage mqttMessage = new MqttMessage(rawPayload.toString().getBytes());
            mqttMessage.setQos(1);

            // Only publish if client is active and connected
            if (this.mqttClient != null && this.mqttClient.isConnected()) {
                this.mqttClient.publish(this.topic, mqttMessage);
            }
        } catch (Exception e) {
            // Suppress printing errors if the client got disconnected/closed during
            // shutdown
            if (this.mqttClient != null && this.mqttClient.isConnected()) {
                System.err.println("[MQTT Sink Error] Gagal mempublikasikan data: " + e.getMessage());
            }
        }
    }

    @Override
    public void onStop() {
    }
}
