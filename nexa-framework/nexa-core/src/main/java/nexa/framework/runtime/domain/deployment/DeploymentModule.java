package nexa.framework.runtime.domain.deployment;

import nexa.framework.runtime.domain.deployment.api.DeploymentService;
import nexa.framework.runtime.domain.deployment.controller.DefaultDeploymentService;
import nexa.framework.runtime.domain.deployment.service.FlowCompiler;
import nexa.framework.runtime.domain.deployment.service.FlowValidator;

/**
 * DeploymentModule merakit kebutuhan internal untuk domain kompilasi/deployment.
 * Bertindak sebagai Composition Root pada tingkat domain.
 */
public final class DeploymentModule {

    private final DeploymentService deploymentService;

    public DeploymentModule() {
        FlowValidator validator = new FlowValidator();
        FlowCompiler compiler = new FlowCompiler(validator);
        this.deploymentService = new DefaultDeploymentService(compiler);
    }

    /**
     * Menyediakan instance DeploymentService untuk domain lain.
     */
    public DeploymentService deploymentService() {
        return deploymentService;
    }
}
