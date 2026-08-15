package nexa.framework.runtime.domain.workspace;

import nexa.framework.runtime.domain.workspace.api.WorkspaceService;
import nexa.framework.runtime.domain.workspace.service.WorkspaceJsonLoader;

/**
 * WorkspaceModule menginisialisasi dan menyediakan dependensi domain workspace.
 * Bertindak sebagai Composition Root pada tingkat domain.
 */
public final class WorkspaceModule {

    private final WorkspaceService workspaceService;

    public WorkspaceModule() {
        // Logika inisialisasi internal domain
        this.workspaceService = new WorkspaceJsonLoader();
    }

    /**
     * Menyediakan instance WorkspaceService yang dapat digunakan oleh domain lain.
     */
    public WorkspaceService workspaceService() {
        return workspaceService;
    }
}

