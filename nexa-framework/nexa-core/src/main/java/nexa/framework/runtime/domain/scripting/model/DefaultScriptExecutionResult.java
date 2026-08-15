package nexa.framework.runtime.domain.scripting.model;

import nexa.framework.runtime.domain.scripting.api.ScriptExecutionResult;

import nexa.framework.runtime.api.model.RuntimeMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultScriptExecutionResult implements ScriptExecutionResult {

    private final Map<String, List<RuntimeMessage>> emittedByPort;
    private final boolean stopped;

    private DefaultScriptExecutionResult(Map<String, List<RuntimeMessage>> emittedByPort, boolean stopped) {
        this.emittedByPort = emittedByPort;
        this.stopped = stopped;
    }

    public static DefaultScriptExecutionResult stoppedResult() {
        return new DefaultScriptExecutionResult(Map.of(), true);
    }

    public static DefaultScriptExecutionResult of(Map<String, List<RuntimeMessage>> emittedByPort) {
        Map<String, List<RuntimeMessage>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, List<RuntimeMessage>> entry : emittedByPort.entrySet()) {
            snapshot.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new DefaultScriptExecutionResult(Map.copyOf(snapshot), false);
    }

    @Override
    public Map<String, List<RuntimeMessage>> emittedByPort() {
        return emittedByPort;
    }

    @Override
    public boolean stopped() {
        return stopped;
    }
}
