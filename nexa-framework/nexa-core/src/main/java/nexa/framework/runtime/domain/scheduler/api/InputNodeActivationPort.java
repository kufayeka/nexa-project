package nexa.framework.runtime.domain.scheduler.api;

import nexa.framework.runtime.domain.scheduler.model.InputNodeRuntimeState;
import nexa.framework.runtime.domain.deployment.model.CompiledNode;

import nexa.framework.runtime.api.model.RuntimeMessage;

import java.time.Duration;

public interface InputNodeActivationPort {

    String flowId();

    boolean isRuntimeStarted();

    boolean isWorkspaceEnabled();

    InputNodeRuntimeState getOrCreateState(CompiledNode inputNode);

    void scheduleAtFixedRate(InputNodeRuntimeState state, Duration interval, Runnable task);

    RuntimeMessage seedMessageForInput(CompiledNode inputNode);

    void executeTriggeredInput(CompiledNode inputNode, RuntimeMessage message);
}
