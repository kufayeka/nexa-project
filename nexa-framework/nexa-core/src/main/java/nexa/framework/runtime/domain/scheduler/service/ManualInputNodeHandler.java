package nexa.framework.runtime.domain.scheduler.service;

import nexa.framework.runtime.domain.scheduler.api.InputNodeHandler;
import nexa.framework.runtime.domain.scheduler.api.InputNodeActivationPort;

import nexa.framework.runtime.domain.deployment.model.CompiledNode;

public final class ManualInputNodeHandler implements InputNodeHandler {

    @Override
    public String nodeType() {
        return "manual-input";
    }

    @Override
    public void activate(CompiledNode inputNode, InputNodeActivationPort activationPort) {
        activationPort.getOrCreateState(inputNode);
    }
}
