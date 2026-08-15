package nexa.framework.runtime.domain.scheduler.service;

import nexa.framework.runtime.domain.scheduler.model.InputNodeRuntimeState;

import nexa.framework.runtime.domain.scheduler.api.InputNodeHandler;
import nexa.framework.runtime.domain.scheduler.api.InputNodeActivationPort;

import nexa.framework.runtime.domain.deployment.model.CompiledNode;
import nexa.framework.runtime.domain.deployment.exception.ValidationException;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.scheduler.helpers.DurationParser;

import java.time.Duration;

public final class TimedTriggerInputNodeHandler implements InputNodeHandler {

    @Override
    public String nodeType() {
        return "timed-trigger";
    }

    @Override
    public void activate(CompiledNode inputNode, InputNodeActivationPort activationPort) {
        Object intervalRaw = inputNode.config().get("interval");
        if (!(intervalRaw instanceof String intervalValue)) {
            throw new ValidationException(
                    "Input node " + inputNode.id() + " in flow " + activationPort.flowId()
                            + " requires string config.interval");
        }

        Duration interval = DurationParser.parseWithMillisecondPrecision(intervalValue);
        InputNodeRuntimeState inputState = activationPort.getOrCreateState(inputNode);

        if (inputState.hasScheduledTrigger()) {
            return;
        }

        activationPort.scheduleAtFixedRate(inputState, interval, () -> {
            if (!activationPort.isRuntimeStarted() || !activationPort.isWorkspaceEnabled()) {
                return;
            }

            RuntimeMessage message = activationPort.seedMessageForInput(inputNode);
            message.writeValue("payload.tickCount", inputState.nextTickCount());

            activationPort.executeTriggeredInput(inputNode, message);
        });
    }
}


