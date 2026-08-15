package nexa.framework.runtime.domain.control;

import nexa.framework.runtime.api.control.WorkspaceControl;
import nexa.framework.runtime.api.control.model.ScriptValidationResult;
import nexa.framework.runtime.api.control.model.ValidationResult;
import nexa.framework.runtime.api.control.model.WorkspaceMetaInfo;
import nexa.framework.runtime.domain.execution.service.DefaultRuntimeEngine;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import nexa.framework.runtime.domain.workspace.service.WorkspaceJsonLoader;
import nexa.framework.runtime.domain.execution.model.WorkspaceRuntime;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultWorkspaceController implements WorkspaceControl {
    private final DefaultRuntimeEngine engine;
    private final WorkspaceJsonLoader jsonLoader = new WorkspaceJsonLoader();
    private final Map<String, String> workspaceRawJsonMap = new ConcurrentHashMap<>();

    public DefaultWorkspaceController(DefaultRuntimeEngine engine) {
        this.engine = engine;
    }

    @Override
    public void loadWorkspace(String jsonSchema) {
        WorkspaceDefinition def = jsonLoader.fromJson(jsonSchema);
        workspaceRawJsonMap.put(def.id(), jsonSchema);
        engine.deploy(def);
    }

    @Override
    public void unloadWorkspace(String workspaceId) {
        workspaceRawJsonMap.remove(workspaceId);
        engine.undeploy(workspaceId);
    }

    @Override
    public void enableWorkspace(String workspaceId) {
        engine.enable(workspaceId);
    }

    @Override
    public void disableWorkspace(String workspaceId) {
        engine.disable(workspaceId);
    }

    @Override
    public List<WorkspaceMetaInfo> getWorkspacesInfo() {
        List<WorkspaceMetaInfo> infoList = new ArrayList<>();
        var workspaces = engine.getWorkspaceRuntimes();
        for (WorkspaceRuntime wr : workspaces.values()) {
            int flowCount = wr.flowsById().size();
            int nodeCount = 0;
            for (var flow : wr.flowsById().values()) {
                nodeCount += flow.compiledFlow().nodeById().size();
            }
            infoList.add(new WorkspaceMetaInfo(
                wr.workspaceId(),
                wr.workspaceId(),
                "Deployed workspace " + wr.workspaceId(),
                wr.enabled(),
                flowCount,
                nodeCount
            ));
        }
        return infoList;
    }

    @Override
    public WorkspaceMetaInfo getWorkspaceInfo(String workspaceId) {
        var workspaces = engine.getWorkspaceRuntimes();
        WorkspaceRuntime wr = workspaces.get(workspaceId);
        if (wr == null) {
            return null;
        }
        int flowCount = wr.flowsById().size();
        int nodeCount = 0;
        for (var flow : wr.flowsById().values()) {
            nodeCount += flow.compiledFlow().nodeById().size();
        }
        return new WorkspaceMetaInfo(
            wr.workspaceId(),
            wr.workspaceId(),
            "Deployed workspace " + wr.workspaceId(),
            wr.enabled(),
            flowCount,
            nodeCount
        );
    }

    @Override
    public String getWorkspaceData(String workspaceId) {
        return workspaceRawJsonMap.get(workspaceId);
    }

    @Override
    public ValidationResult validateWorkspace(String jsonSchema) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            jsonLoader.fromJson(jsonSchema);
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    @Override
    public ScriptValidationResult validateNodeScript(String language, String script) {
        Map<String, Object> errorContainer = new HashMap<>();
        boolean valid = engine.validateScript(language, script, errorContainer);
        List<String> errors = new ArrayList<>();
        if (!valid) {
            errors.add(errorContainer.getOrDefault("message", "Script validation failed").toString());
        }
        return new ScriptValidationResult(valid, language, errors);
    }
}
