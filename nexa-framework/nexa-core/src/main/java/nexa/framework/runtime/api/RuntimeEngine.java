package nexa.framework.runtime.api;

import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.statistics.model.RuntimeStatisticsSnapshot;

public interface RuntimeEngine {

    void startRuntime();

    void stopRuntime();

    void deploy(WorkspaceDefinition workspaceDefinition);

    void undeploy(String workspaceId);

    void disable(String workspaceId);

    void enable(String workspaceId);

    void trigger(String workspaceId, String flowId, String inputNodeId, RuntimeMessage message);

    void setNodeEnabled(String workspaceId, String flowId, String nodeId, boolean enabled);

    RuntimeStatisticsSnapshot statistics(String workspaceId, String flowId);
}

