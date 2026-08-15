package nexa.framework.runtime.domain.execution.api;

import nexa.framework.runtime.domain.deployment.model.CompiledWorkspace;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.statistics.model.RuntimeStatisticsSnapshot;

/**
 * ExecutionService mendefinisikan antarmuka utama untuk mengontrol siklus hidup runtime,
 * melakukan deployment workspace yang telah dikompilasi, serta memicu trigger eksekusi flow.
 */
public interface ExecutionService {

    void startRuntime();

    void stopRuntime();

    void deploy(CompiledWorkspace compiledWorkspace);

    void undeploy(String workspaceId);

    void disable(String workspaceId);

    void enable(String workspaceId);

    void trigger(String workspaceId, String flowId, String inputNodeId, RuntimeMessage message);

    void setNodeEnabled(String workspaceId, String flowId, String nodeId, boolean enabled);

    RuntimeStatisticsSnapshot statistics(String workspaceId, String flowId);

    boolean isRuntimeStarted();

    boolean isWorkspaceEnabled(String workspaceId);

    void executeTriggeredInput(String workspaceId, String flowId, String inputNodeId, RuntimeMessage seedMessage);
}
