package nexa.framework.runtime.domain.scripting.bytecode;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** Runtime bindings used by bytecode. Host integrations stay outside the VM. */
public final class NexaBytecodeExecutionContext {
    private final Map<String, Object> globals;
    private final BiFunction<String, Object[], Object> hostCall;

    public NexaBytecodeExecutionContext(Map<String, Object> globals,
                                        BiFunction<String, Object[], Object> hostCall) {
        this.globals = globals == null ? Map.of() : Map.copyOf(globals);
        this.hostCall = hostCall == null ? (name, args) -> {
            throw new IllegalStateException("No host function registered: " + name);
        } : hostCall;
    }

    public Object global(String name) {
        if (!globals.containsKey(name)) {
            throw new IllegalStateException("Unknown global: " + name);
        }
        return globals.get(name);
    }

    public Object callHost(String name, Object[] args) {
        return Objects.requireNonNull(hostCall.apply(name, args), "host function returned null") == null
                ? null : hostCall.apply(name, args);
    }
}
