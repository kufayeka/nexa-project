package nexa.plugin.mqtt.resource;

import nexa.framework.runtime.api.plugin.NexaResourcePlugin;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.plugin.mqtt.manager.MqttBrokerManager;
import nexa.plugin.mqtt.manager.MqttBrokerManager.PublishConfig;
import org.eclipse.paho.client.mqttv3.MqttClient;

import java.util.Map;

/**
 * Shared MQTT client pool resource.
 *
 * Resource configuration controls both the physical MQTT connection and the
 * outbound publish infrastructure shared by MQTT sink nodes using this pool.
 */
public final class MqttClientPoolPlugin implements NexaResourcePlugin {
    private String targetId;
    private String name;
    private String brokerUrl;
    private int keepAlive;
    private MqttClient mqttClient;
    private PublishConfig publishConfig;

    @Override
    public String getPluginType() {
        return "mqtt-client-pool";
    }

    @Override
    public Object getNativeClient() {
        return this.mqttClient;
    }

    @Override
    public void onInit(
            final String targetId,
            final Map<String, Object> config,
            final NexaPluginContext context) throws Exception {
        this.targetId = targetId;
        this.name = (String) config.get("name");
        this.brokerUrl = (String) config.getOrDefault("brokerUrl", "tcp://localhost:1883");
        this.keepAlive = ((Number) config.getOrDefault("keepAlive", 60)).intValue();
        this.publishConfig = PublishConfig.fromConfig(config);
    }

    @Override
    public void onStart() throws Exception {
        this.mqttClient = MqttBrokerManager.getOrCreateClient(
                this.brokerUrl,
                this.keepAlive,
                this.publishConfig);

        MqttBrokerManager.registerClientReference(
                this.targetId,
                this.name,
                this.mqttClient);
    }

    @Override
    public void onStop() {
        MqttBrokerManager.unregisterClientReference(this.targetId, this.name);
        MqttBrokerManager.removeClient(this.brokerUrl);
    }
}
