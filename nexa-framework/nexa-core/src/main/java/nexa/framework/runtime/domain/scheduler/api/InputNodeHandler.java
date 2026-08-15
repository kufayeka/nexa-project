package nexa.framework.runtime.domain.scheduler.api;

import nexa.framework.runtime.domain.deployment.model.CompiledNode;

public interface InputNodeHandler {

    String nodeType();

    void activate(CompiledNode inputNode, InputNodeActivationPort activationPort);
}


