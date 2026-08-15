package nexa.framework.runtime.domain.execution.controller;

import nexa.framework.runtime.domain.deployment.model.CompiledWorkspace;
import nexa.framework.runtime.domain.execution.api.ExecutionService;
import nexa.framework.runtime.domain.execution.model.WorkspaceRuntime;
import nexa.framework.runtime.domain.execution.model.FlowRuntime;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.execution.service.RuntimeExecutionService;
import nexa.framework.runtime.domain.statistics.model.RuntimeStatisticsSnapshot;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultExecutionService implements ExecutionService {
    private final RuntimeExecutionService executionEngine;
    private final ConcurrentMap<String, WorkspaceRuntime> workspaces;
    private final AtomicBoolean runtimeStarted;

    public DefaultExecutionService(RuntimeExecutionService executionEngine,
            ConcurrentMap<String, WorkspaceRuntime> workspaces, AtomicBoolean runtimeStarted) {
        this.executionEngine = executionEngine;
        this.workspaces = workspaces;
        this.runtimeStarted = runtimeStarted;
    }

    @Override
    public void startRuntime() {
        executionEngine.startRuntime(runtimeStarted, workspaces);
    }

    @Override
    public void stopRuntime() {
        executionEngine.stopRuntime(runtimeStarted, workspaces);
    }

    @Override
    public void deploy(CompiledWorkspace compiledWorkspace) {
        WorkspaceRuntime newRuntime = new WorkspaceRuntime(compiledWorkspace.workspaceId(),
                compiledWorkspace.enabled());
        for (var compiledFlow : compiledWorkspace.flowsById().values()) {
            newRuntime.flowsById().put(compiledFlow.flowId(),
                    new FlowRuntime(compiledWorkspace.workspaceId(), compiledFlow));
        }
        WorkspaceRuntime previous = workspaces.put(compiledWorkspace.workspaceId(), newRuntime);
        if (previous != null)
            executionEngine.stopWorkspaceRuntime(previous);
        if (runtimeStarted.get() && newRuntime.enabled())
            executionEngine.activateWorkspaceInputs(newRuntime, runtimeStarted);
    }

    @Override
    public void undeploy(String workspaceId) {
        WorkspaceRuntime removed = workspaces.remove(workspaceId);
        if (removed != null)
            executionEngine.stopWorkspaceRuntime(removed);
    }

    @Override
    public void disable(String workspaceId) {
        WorkspaceRuntime workspaceRuntime = RuntimeExecutionService.requireWorkspace(workspaces, workspaceId);
        workspaceRuntime.setEnabled(false);
        // Stop accepting new input, but let executions already in flight finish.
        executionEngine.disableWorkspaceRuntime(workspaceRuntime);
    }

    @Override
    public void enable(String workspaceId) {
        WorkspaceRuntime workspaceRuntime = RuntimeExecutionService.requireWorkspace(workspaces, workspaceId);
        workspaceRuntime.setEnabled(true);
        if (runtimeStarted.get())
            executionEngine.activateWorkspaceInputs(workspaceRuntime, runtimeStarted);
    }

    @Override
    public void trigger(String workspaceId, String flowId, String inputNodeId, RuntimeMessage message) {
        executionEngine.trigger(workspaces, workspaceId, flowId, inputNodeId, message);
    }

    @Override
    public void setNodeEnabled(String workspaceId, String flowId, String nodeId, boolean enabled) {
        executionEngine.setNodeEnabled(workspaces, runtimeStarted, workspaceId, flowId, nodeId, enabled);
    }

    @Override
    public RuntimeStatisticsSnapshot statistics(String workspaceId, String flowId) {
        return executionEngine.statistics(workspaces, workspaceId, flowId);
    }

    @Override
    public boolean isRuntimeStarted() {
        return runtimeStarted.get();
    }

    @Override
    public boolean isWorkspaceEnabled(String workspaceId) {
        WorkspaceRuntime workspaceRuntime = workspaces.get(workspaceId);
        return workspaceRuntime != null && workspaceRuntime.enabled();
    }

    @Override
    public void executeTriggeredInput(String workspaceId, String flowId, String inputNodeId,
            RuntimeMessage seedMessage) {
        WorkspaceRuntime workspaceRuntime = workspaces.get(workspaceId);
        if (workspaceRuntime == null || !workspaceRuntime.enabled())
            return;
        FlowRuntime flowRuntime = workspaceRuntime.flowsById().get(flowId);
        if (flowRuntime == null || !flowRuntime.compiledFlow().enabled())
            return;
        var inputNode = flowRuntime.compiledFlow().node(inputNodeId);
        if (inputNode != null && inputNode.enabled()) {
            executionEngine.executeTriggeredInput(workspaceRuntime, flowRuntime, inputNode, seedMessage);
        }
    }
}
