package nexa.framework.runtime.domain.scheduler.service;

import nexa.framework.runtime.domain.execution.api.ExecutionService;
import nexa.framework.runtime.domain.execution.api.InputActivator;
import nexa.framework.runtime.domain.execution.model.FlowRuntime;
import nexa.framework.runtime.domain.execution.model.WorkspaceRuntime;
import nexa.framework.runtime.domain.deployment.model.CompiledNode;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import nexa.framework.runtime.domain.scheduler.api.InputNodeActivationPort;
import nexa.framework.runtime.domain.scheduler.api.InputNodeHandler;
import nexa.framework.runtime.domain.scheduler.registry.InputNodeHandlerRegistry;
import nexa.framework.runtime.domain.scheduler.model.InputNodeRuntimeState;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.api.helpers.DeepCopyUtil;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * InputActivationService bertindak sebagai implementasi InputActivator dari domain scheduler.
 * Mengelola siklus hidup pemicu input node secara asinkronus menggunakan scheduler.
 */
public final class InputActivationService implements InputActivator {

    private final InputNodeHandlerRegistry inputNodeHandlerRegistry;
    private final ScheduledExecutorService scheduler;
    private final ExecutionService executionService;

    public InputActivationService(
            InputNodeHandlerRegistry inputNodeHandlerRegistry,
            ScheduledExecutorService scheduler,
            ExecutionService executionService) {
        this.inputNodeHandlerRegistry = inputNodeHandlerRegistry;
        this.scheduler = scheduler;
        this.executionService = executionService;
    }

    @Override
    public void activateWorkspaceInputs(WorkspaceRuntime workspaceRuntime, AtomicBoolean runtimeStarted) {
        for (FlowRuntime flowRuntime : workspaceRuntime.flowsById().values()) {
            if (!flowRuntime.compiledFlow().enabled()) {
                continue;
            }

            for (String inputNodeId : flowRuntime.compiledFlow().inputNodeIds()) {
                activateInputNodeInternal(workspaceRuntime, flowRuntime, inputNodeId, runtimeStarted);
            }
        }
    }

    @Override
    public void activateInputNode(WorkspaceRuntime workspaceRuntime, String flowId, String nodeId, AtomicBoolean runtimeStarted) {
        FlowRuntime flowRuntime = workspaceRuntime.flowsById().get(flowId);
        if (flowRuntime != null) {
            activateInputNodeInternal(workspaceRuntime, flowRuntime, nodeId, runtimeStarted);
        }
    }

    @Override
    public void stopWorkspaceRuntime(WorkspaceRuntime workspaceRuntime) {
        for (FlowRuntime flowRuntime : workspaceRuntime.flowsById().values()) {
            for (InputNodeRuntimeState inputState : flowRuntime.inputStateByNodeId().values()) {
                inputState.cancelAllScheduledTriggers();
            }
        }
    }

    private void activateInputNodeInternal(
            WorkspaceRuntime workspaceRuntime,
            FlowRuntime flowRuntime,
            String inputNodeId,
            AtomicBoolean runtimeStarted) {
        CompiledNode inputNode = flowRuntime.compiledFlow().node(inputNodeId);
        if (inputNode == null || inputNode.category() != NodeCategory.INPUT || !inputNode.enabled()) {
            return;
        }

        if (nexa.framework.runtime.domain.scripting.registry.PluginRegistry.hasPlugin(inputNode.type())) {
            return;
        }

        InputNodeHandler handler = inputNodeHandlerRegistry.requireHandler(inputNode.type());
        handler.activate(inputNode, newInputActivationPort(workspaceRuntime, flowRuntime, runtimeStarted));
    }

    private InputNodeActivationPort newInputActivationPort(
            WorkspaceRuntime workspaceRuntime,
            FlowRuntime flowRuntime,
            AtomicBoolean runtimeStarted) {
        return new InputNodeActivationPort() {
            @Override
            public String flowId() {
                return flowRuntime.compiledFlow().flowId();
            }

            @Override
            public boolean isRuntimeStarted() {
                return runtimeStarted.get();
            }

            @Override
            public boolean isWorkspaceEnabled() {
                return workspaceRuntime.enabled();
            }

            @Override
            public InputNodeRuntimeState getOrCreateState(CompiledNode inputNode) {
                return flowRuntime.inputStateByNodeId().computeIfAbsent(
                        inputNode.id(),
                        ignored -> new InputNodeRuntimeState(inputNode.inputPolicy().maxConcurrentExecutions()));
            }

            @Override
            public void scheduleAtFixedRate(InputNodeRuntimeState state, Duration interval, Runnable task) {
                ScheduledFuture<?> scheduled = scheduler.scheduleAtFixedRate(
                        task,
                        0,
                        interval.toMillis(),
                        TimeUnit.MILLISECONDS);

                state.addScheduledTrigger(scheduled);
            }

            @Override
            public RuntimeMessage seedMessageForInput(CompiledNode inputNode) {
                return InputActivationService.this.seedMessageForInput(inputNode);
            }

            @Override
            public void executeTriggeredInput(CompiledNode inputNode, RuntimeMessage message) {
                executionService.executeTriggeredInput(
                        workspaceRuntime.workspaceId(),
                        flowRuntime.compiledFlow().flowId(),
                        inputNode.id(),
                        message);
            }
        };
    }

    private RuntimeMessage seedMessageForInput(CompiledNode inputNode) {
        Object payloadRaw = inputNode.config().get("payload");
        if (payloadRaw instanceof Map<?, ?> payloadMap) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : payloadMap.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), DeepCopyUtil.deepCopyValue(entry.getValue()));
            }
            return new RuntimeMessage(converted);
        }

        return new RuntimeMessage();
    }
}
