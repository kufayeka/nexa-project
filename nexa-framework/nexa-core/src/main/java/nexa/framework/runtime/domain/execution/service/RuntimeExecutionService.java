package nexa.framework.runtime.domain.execution.service;

import nexa.framework.runtime.domain.execution.model.FlowRuntime;
import nexa.framework.runtime.domain.execution.model.WorkspaceRuntime;
import nexa.framework.runtime.api.OutputConsumer;
import nexa.framework.runtime.api.RuntimeConfiguration;
import nexa.framework.runtime.domain.deployment.model.CompiledNode;
import nexa.framework.runtime.domain.deployment.exception.ValidationException;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import nexa.framework.runtime.domain.execution.api.InputActivator;
import nexa.framework.runtime.domain.scheduler.model.InputNodeRuntimeState;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.statistics.model.RuntimeStatisticsSnapshot;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RuntimeExecutionService adalah pusat orkestrasi internal domain execution.
 */
public final class RuntimeExecutionService {

    private final RuntimeConfiguration configuration;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService workerExecutor;
    private InputActivator inputActivator;
    private final ExecutionLifecycleManager lifecycleManager;
    private final NodeExecutor nodeExecutor;

    public RuntimeExecutionService(
            RuntimeConfiguration configuration,
            OutputConsumer outputConsumer,
            nexa.framework.runtime.domain.control.DefaultNodeController nodeController,
            nexa.framework.runtime.domain.control.DefaultConnectionController connectionController,
            nexa.framework.runtime.domain.control.DefaultNexaEventBus eventBus) {
        this.configuration = configuration;
        this.scheduler = Executors.newScheduledThreadPool(2,
                Thread.ofPlatform().daemon(true).name("nexa-scheduler-", 0).factory());
        this.workerExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("nexa-worker-", 0).factory());
        this.nodeExecutor = new NodeExecutor(outputConsumer, workerExecutor, this, nodeController, connectionController, eventBus);
        this.lifecycleManager = new ExecutionLifecycleManager(configuration, scheduler, this);
    }

    public void setInputActivator(InputActivator inputActivator) {
        this.inputActivator = inputActivator;
    }

    public void executeTriggeredInput(
            WorkspaceRuntime workspaceRuntime,
            FlowRuntime flowRuntime,
            CompiledNode inputNode,
            RuntimeMessage seedMessage) {
        lifecycleManager.executeTriggeredInput(workspaceRuntime, flowRuntime, inputNode, seedMessage);
    }

    public ExecutionLifecycleManager lifecycleManager() {
        return lifecycleManager;
    }

    public NodeExecutor nodeExecutor() {
        return nodeExecutor;
    }

    public ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public void startRuntime(AtomicBoolean runtimeStarted, ConcurrentMap<String, WorkspaceRuntime> workspaces) {
        if (!runtimeStarted.compareAndSet(false, true)) {
            return;
        }
        for (WorkspaceRuntime workspaceRuntime : workspaces.values()) {
            if (workspaceRuntime.enabled()) {
                activateWorkspaceInputs(workspaceRuntime, runtimeStarted);
            }
        }
    }

    public void stopRuntime(AtomicBoolean runtimeStarted, ConcurrentMap<String, WorkspaceRuntime> workspaces) {
        if (!runtimeStarted.compareAndSet(true, false)) {
            return;
        }
        for (WorkspaceRuntime workspaceRuntime : workspaces.values()) {
            stopWorkspaceRuntime(workspaceRuntime);
        }
    }

    public RuntimeStatisticsSnapshot statistics(
            ConcurrentMap<String, WorkspaceRuntime> workspaces,
            String workspaceId,
            String flowId) {
        WorkspaceRuntime workspaceRuntime = requireWorkspace(workspaces, workspaceId);
        FlowRuntime flowRuntime = requireFlow(workspaceRuntime, flowId);
        return flowRuntime.statistics().snapshot();
    }

    public void setNodeEnabled(
            ConcurrentMap<String, WorkspaceRuntime> workspaces,
            AtomicBoolean runtimeStarted,
            String workspaceId,
            String flowId,
            String nodeId,
            boolean enabled) {
        WorkspaceRuntime workspaceRuntime = requireWorkspace(workspaces, workspaceId);
        FlowRuntime flowRuntime = requireFlow(workspaceRuntime, flowId);

        flowRuntime.compiledFlow().setNodeEnabled(nodeId, enabled);
        flowRuntime.refreshNodeRuntime(nodeId);

        if (!enabled) {
            InputNodeRuntimeState inputState = flowRuntime.inputStateByNodeId().get(nodeId);
            if (inputState != null) {
                inputState.cancelAllScheduledTriggers();
            }
        }

        if (enabled && runtimeStarted.get() && workspaceRuntime.enabled() && inputActivator != null) {
            inputActivator.activateInputNode(workspaceRuntime, flowId, nodeId, runtimeStarted);
        }
    }

    public void trigger(
            ConcurrentMap<String, WorkspaceRuntime> workspaces,
            String workspaceId,
            String flowId,
            String inputNodeId,
            RuntimeMessage message) {
        WorkspaceRuntime workspaceRuntime = requireWorkspace(workspaces, workspaceId);
        if (!workspaceRuntime.enabled()) {
            return;
        }

        FlowRuntime flowRuntime = requireFlow(workspaceRuntime, flowId);
        if (!flowRuntime.compiledFlow().enabled()) {
            return;
        }

        CompiledNode inputNode = flowRuntime.compiledFlow().node(inputNodeId);
        if (inputNode == null || inputNode.category() != NodeCategory.INPUT || !inputNode.enabled()) {
            throw new ValidationException("Invalid input node " + inputNodeId + " for flow " + flowId);
        }

        RuntimeMessage seed = message == null ? new RuntimeMessage() : message.deepCopy();
        lifecycleManager.executeTriggeredInput(workspaceRuntime, flowRuntime, inputNode, seed);
    }

    public void activateWorkspaceInputs(WorkspaceRuntime workspaceRuntime, AtomicBoolean runtimeStarted) {
        if (inputActivator != null) {
            inputActivator.activateWorkspaceInputs(workspaceRuntime, runtimeStarted);
        }
    }

    public void stopWorkspaceRuntime(WorkspaceRuntime workspaceRuntime) {
        if (inputActivator != null) {
            inputActivator.stopWorkspaceRuntime(workspaceRuntime);
        }
        lifecycleManager.stopWorkspaceRuntime(workspaceRuntime);
    }

    public static WorkspaceRuntime requireWorkspace(ConcurrentMap<String, WorkspaceRuntime> workspaces,
            String workspaceId) {
        WorkspaceRuntime workspaceRuntime = workspaces.get(workspaceId);
        if (workspaceRuntime == null) {
            throw new ValidationException("Workspace " + workspaceId + " not deployed");
        }
        return workspaceRuntime;
    }

    private static FlowRuntime requireFlow(WorkspaceRuntime workspaceRuntime, String flowId) {
        FlowRuntime flowRuntime = workspaceRuntime.flowsById().get(flowId);
        if (flowRuntime == null) {
            throw new ValidationException(
                    "Flow " + flowId + " not found in workspace " + workspaceRuntime.workspaceId());
        }
        return flowRuntime;
    }

    public void injectMessage(
            WorkspaceRuntime workspaceRuntime,
            FlowRuntime flowRuntime,
            String sourceNodeId,
            RuntimeMessage message) {
        lifecycleManager.injectMessage(workspaceRuntime, flowRuntime, sourceNodeId, message);
    }

    public void injectMessageIntoConnection(
            WorkspaceRuntime workspaceRuntime,
            FlowRuntime flowRuntime,
            String connectionId,
            RuntimeMessage message) {
        lifecycleManager.injectMessageIntoConnection(workspaceRuntime, flowRuntime, connectionId, message);
    }
}
