package nexa.framework.runtime.api;

import nexa.framework.runtime.api.model.RuntimeMessage;

import java.util.List;

/** Runtime services exposed to generated Nexa bytecode. */
public interface NexaExecutionContext {
    void send(RuntimeMessage msg);
    void send(String port, RuntimeMessage msg);
    void send(List<String> ports, RuntimeMessage msg);
    Object callHostCapability(String namespace, String name, List<Object> args);
}
