package nexa.framework.runtime.domain.deployment;

import nexa.framework.runtime.domain.deployment.api.DeploymentService;
import nexa.framework.runtime.domain.deployment.controller.DefaultDeploymentService;
import nexa.framework.runtime.domain.deployment.service.FlowCompiler;
import nexa.framework.runtime.domain.deployment.service.FlowValidator;
import nexa.framework.runtime.domain.scripting.registry.ScriptEngineRegistry;

/**
 * DeploymentModule merakit kebutuhan internal untuk domain kompilasi/deployment.
 * Bertindak sebagai Composition Root pada tingkat domain.
 */
public final class DeploymentModule {

    private final DeploymentService deploymentService;

    // Dependensi disuntikkan secara eksplisit via constructor
    public DeploymentModule(ScriptEngineRegistry scriptEngineRegistry) {
        FlowValidator validator = new FlowValidator();
        // FlowCompiler membutuhkan validator dan script registry dari domain scripting
        FlowCompiler compiler = new FlowCompiler(validator, scriptEngineRegistry);
        this.deploymentService = new DefaultDeploymentService(compiler);
    }

    /**
     * Menyediakan instance DeploymentService untuk domain lain.
     */
    public DeploymentService deploymentService() {
        return deploymentService;
    }
}
