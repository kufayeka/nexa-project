package nexa.plugin.asset.script;

import nexa.framework.runtime.domain.scripting.internal.nexa.NexaHostObject;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaScriptException;

public final class SelfHostObject implements NexaHostObject {

    @Override
    public Object member(String name, int line, int column) {
        ScriptSelfContext.Self self = ScriptSelfContext.getContext();
        if (self == null) {
            throw new NexaScriptException("Object 'self' hanya bisa diakses di dalam runtime script attribute calculation.", line, column);
        }

        return switch (name) {
            case "value" -> self.value();
            case "oldValue" -> self.oldValue();
            case "newValue" -> self.newValue();
            case "timestamp" -> self.timestamp();
            case "quality" -> self.quality();
            default -> throw new NexaScriptException("Property 'self." + name + "' tidak didukung.", line, column);
        };
    }
}
