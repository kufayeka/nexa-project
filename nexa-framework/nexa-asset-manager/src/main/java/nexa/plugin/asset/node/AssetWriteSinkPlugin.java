package nexa.plugin.asset.node;

import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.framework.runtime.api.plugin.NexaSinkPlugin;
import nexa.plugin.asset.resource.AssetManagerResourcePlugin;

import java.util.Map;

public final class AssetWriteSinkPlugin implements NexaSinkPlugin {
    private String attributePath;
    private String valueSource;

    @Override
    public String getPluginType() {
        return "asset-write";
    }

    @Override
    public void onInit(String targetId, Map<String, Object> config, NexaPluginContext context) throws Exception {
        this.attributePath = (String) config.get("attributePath");
        if (this.attributePath == null || this.attributePath.isBlank()) {
            throw new IllegalArgumentException("Asset Write Output Node requires 'attributePath' configuration.");
        }
        this.valueSource = (String) config.getOrDefault("valueSource", "payload.value");
    }

    @Override
    public void onStart() throws Exception {
    }

    @Override
    public void consume(RuntimeMessage msg) {
        AssetManagerResourcePlugin manager = AssetManagerResourcePlugin.getActiveInstance();
        if (manager == null) {
            System.err.println("[Asset Write Sink Error] Asset Manager is not started or active.");
            return;
        }
        Object value = msg.readRawValue(valueSource);
        if (value != null) {
            manager.write(attributePath, value);
        }
    }

    @Override
    public void onStop() {
    }
}
