package nexa.framework.runtime.api;

import nexa.framework.runtime.api.model.RuntimeMessage;

/** Stable execution boundary implemented by AOT-compiled Nexa bytecode. */
public interface NexaCompiledNode {
    void execute(RuntimeMessage msg, NexaExecutionContext context);
}
