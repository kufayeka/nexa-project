package nexa.framework.runtime.domain.deployment.controller;

import nexa.framework.runtime.domain.deployment.api.DeploymentService;
import nexa.framework.runtime.domain.deployment.model.CompiledWorkspace;
import nexa.framework.runtime.domain.deployment.service.FlowCompiler;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;

/**
 * DefaultDeploymentService bertindak sebagai kontroler luar domain untuk memfasilitasi
 * kompilasi workspace flow.
 */
public final class DefaultDeploymentService implements DeploymentService {

    private final FlowCompiler compiler;

    public DefaultDeploymentService(FlowCompiler compiler) {
        this.compiler = compiler;
    }

    @Override
    public CompiledWorkspace compile(WorkspaceDefinition definition) {
        return compiler.compileWorkspace(definition);
    }

    @Override
    public void invalidateWorkspace(String workspaceId) {
        compiler.invalidateWorkspaceScripts(workspaceId);
    }
}
