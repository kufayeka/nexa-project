package nexa.plugin.asset.script;

import nexa.framework.runtime.domain.scripting.internal.nexa.NexaHostObject;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaScriptException;
import nexa.plugin.asset.resource.AssetManagerResourcePlugin;

public final class SelfHostObject implements NexaHostObject {

    @Override
    public Object member(String name, int line, int column) {
        AssetManagerResourcePlugin manager = AssetManagerResourcePlugin.getActiveInstance();
        AssetScriptContext context = manager != null
            ? AssetScriptingEngine.forManager(manager).currentContext()
            : null;

        if (context == null) {
            throw new NexaScriptException(
                "Object 'self' hanya bisa diakses di dalam runtime script attribute calculation.",
                line,
                column
            );
        }

        AssetScriptContext.Self self = context.self();
        return switch (name) {
            case "value" -> self.value();
            case "oldValue" -> self.oldValue();
            case "newValue" -> self.newValue();
            case "timestamp" -> self.timestamp();
            case "quality" -> self.quality();
            default -> throw new NexaScriptException(
                "Property 'self." + name + "' tidak didukung.",
                line,
                column
            );
        };
    }
}
