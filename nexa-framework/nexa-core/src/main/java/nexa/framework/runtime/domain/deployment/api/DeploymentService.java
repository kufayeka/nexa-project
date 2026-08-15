package nexa.framework.runtime.domain.deployment.api;

import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import nexa.framework.runtime.domain.deployment.model.CompiledWorkspace;

/**
 * DeploymentService mendefinisikan antarmuka untuk kompilasi dan validasi workspace.
 */
public interface DeploymentService {

    /**
     * Mengompilasi WorkspaceDefinition menjadi CompiledWorkspace.
     */
    CompiledWorkspace compile(WorkspaceDefinition definition);

    /**
     * Membatalkan kompilasi / membersihkan cache skrip untuk workspace tertentu.
     */
    void invalidateWorkspace(String workspaceId);
}
