package nexa.framework.runtime.api.control;

import nexa.framework.runtime.api.control.model.*;
import java.util.List;

public interface WorkspaceControl {
    void loadWorkspace(String jsonSchema);

    void unloadWorkspace(String workspaceId);

    void enableWorkspace(String workspaceId);

    void disableWorkspace(String workspaceId);

    List<WorkspaceMetaInfo> getWorkspacesInfo();

    WorkspaceMetaInfo getWorkspaceInfo(String workspaceId);

    String getWorkspaceData(String workspaceId);

    ValidationResult validateWorkspace(String jsonSchema);

    ScriptValidationResult validateNodeScript(String language, String script);
}