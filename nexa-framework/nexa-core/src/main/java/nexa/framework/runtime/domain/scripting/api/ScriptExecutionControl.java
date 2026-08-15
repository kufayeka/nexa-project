package nexa.framework.runtime.domain.scripting.api;

import nexa.framework.runtime.domain.scripting.exception.StopScriptExecutionException;

import nexa.framework.runtime.api.model.RuntimeMessage;

import java.util.List;
import java.util.Map;

public final class ScriptExecutionControl {

    private final ScriptEmissionCollector collector;

    public ScriptExecutionControl(ScriptEmissionCollector collector) {
        this.collector = collector;
    }

    public void send(Object message) {
        collector.emit("default", toRuntimeMessage(message));
    }

    public void send(String port, Object message) {
        collector.emit(port, toRuntimeMessage(message));
    }

    public void send(List<String> ports, Object message) {
        RuntimeMessage runtimeMessage = toRuntimeMessage(message);
        int totalPorts = ports.size();
        for (int index = 0; index < totalPorts; index++) {
            String port = ports.get(index);
            RuntimeMessage emitted = index == 0
                    ? runtimeMessage
                    : runtimeMessage.deepCopy();
            collector.emit(port, emitted);
        }
    }

    public void stop() {
        throw new StopScriptExecutionException();
    }

    private RuntimeMessage toRuntimeMessage(Object value) {
        if (value instanceof RuntimeMessage runtimeMessage) {
            return runtimeMessage.deepCopy();
        }

        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> converted = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return new RuntimeMessage(converted);
        }

        throw new IllegalArgumentException("send expects RuntimeMessage or Map as message argument");
    }

    public interface ScriptEmissionCollector {
        void emit(String port, RuntimeMessage message);
    }
}


