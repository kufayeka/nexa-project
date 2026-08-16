package nexa.plugin.asset.node;

import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.framework.runtime.api.plugin.NexaSourcePlugin;
import nexa.plugin.asset.resource.AssetManagerResourcePlugin;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class AssetReadInputPlugin implements NexaSourcePlugin {
    private String attributePath;
    private String fireMode;      // "ON_CHANGE" or "ON_UPDATE"
    private String outputMode;    // "VALUE" or "VTQ"
    private Consumer<RuntimeMessage> emitter;
    private AssetManagerResourcePlugin.AttributeListener listener;

    @Override
    public String getPluginType() {
        return "asset-read";
    }

    @Override
    public void setEmitter(Consumer<RuntimeMessage> emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onInit(String targetId, Map<String, Object> config, NexaPluginContext context) throws Exception {
        this.attributePath = (String) config.get("attributePath");
        if (this.attributePath == null || this.attributePath.isBlank()) {
            throw new IllegalArgumentException("Asset Read Input Node requires 'attributePath' configuration.");
        }
        this.fireMode = (String) config.getOrDefault("fireMode", "ON_CHANGE");
        this.outputMode = (String) config.getOrDefault("outputMode", "VALUE");
    }

    @Override
    public void onStart() throws Exception {
        AssetManagerResourcePlugin manager = AssetManagerResourcePlugin.getActiveInstance();
        if (manager == null) {
            throw new IllegalStateException("Asset Manager Plugin is not active. Make sure it is registered as a resource.");
        }
        this.listener = (path, value, oldValue, timestamp, quality) -> {
            if ("ON_CHANGE".equalsIgnoreCase(fireMode)) {
                if (Objects.equals(value, oldValue)) {
                    return; // Ignore if value is identical
                }
            }
            emitValue(value, oldValue, timestamp, quality);
        };
        manager.registerListener(attributePath, listener);
    }

    private void emitValue(Object value, Object oldValue, long timestamp, String quality) {
        if (emitter == null) return;
        RuntimeMessage msg = new RuntimeMessage();
        if ("VTQ".equalsIgnoreCase(outputMode)) {
            msg.writeValue("payload.value", value != null ? value : "null");
            msg.writeValue("payload.oldValue", oldValue != null ? oldValue : "null");
            msg.writeValue("payload.timestamp", timestamp);
            msg.writeValue("payload.quality", quality);
        } else {
            msg.writeValue("payload.value", value);
        }
        emitter.accept(msg);
    }

    @Override
    public void onStop() {
        AssetManagerResourcePlugin manager = AssetManagerResourcePlugin.getActiveInstance();
        if (manager != null && listener != null) {
            manager.unregisterListener(attributePath, listener);
        }
    }
}
