package nexa.framework.runtime.domain.execution.service;

import nexa.framework.runtime.domain.execution.model.ActiveExecution;
import nexa.framework.runtime.domain.execution.model.FlowRuntime;
import nexa.framework.runtime.domain.execution.model.NodeRuntime;

import nexa.framework.runtime.api.OutputConsumer;
import nexa.framework.runtime.domain.deployment.model.CompiledNode;
import nexa.framework.runtime.domain.deployment.exception.ValidationException;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import nexa.framework.runtime.domain.execution.model.ExecutionContext;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.scripting.api.ScriptExecutionResult;
import nexa.framework.runtime.domain.scripting.model.ScriptRuntimeContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

final class NodeExecutor {

    private static final String DEFAULT_PORT = "default";

    private final OutputConsumer outputConsumer;
    private final ExecutorService workerExecutor;
    private final RuntimeExecutionService executionService;
    private final nexa.framework.runtime.domain.control.DefaultNodeController nodeController;
    private final nexa.framework.runtime.domain.control.DefaultConnectionController connectionController;
    private final nexa.framework.runtime.domain.control.DefaultNexaEventBus eventBus;

    NodeExecutor(
            OutputConsumer outputConsumer,
            ExecutorService workerExecutor,
            RuntimeExecutionService executionService,
            nexa.framework.runtime.domain.control.DefaultNodeController nodeController,
            nexa.framework.runtime.domain.control.DefaultConnectionController connectionController,
            nexa.framework.runtime.domain.control.DefaultNexaEventBus eventBus) {
        this.outputConsumer = outputConsumer;
        this.workerExecutor = workerExecutor;
        this.executionService = executionService;
        this.nodeController = nodeController;
        this.connectionController = connectionController;
        this.eventBus = eventBus;
    }

    public void submitNodeRoutes(
            FlowRuntime flowRuntime,
            UUID executionId,
            String sourceNodeId,
            String sourcePort,
            RuntimeMessage message) {
        ActiveExecution activeExecution = flowRuntime.activeExecutions().get(executionId);
        if (activeExecution == null) {
            return;
        }

        if (activeExecution.context().isCancellationRequested()) {
            return;
        }

        if (connectionController.isConnectionDisabled(sourceNodeId)) {
            return;
        }
        connectionController.incrementMessageCount(sourceNodeId);

        List<NodeRuntime> targets = flowRuntime.targets(sourceNodeId, sourcePort);
        int totalTargets = targets.size();
        System.out.println("[NODE EXECUTOR] submitNodeRoutes from: " + sourceNodeId + " to port: " + sourcePort + " | targets found: " + totalTargets);
        for (int index = 0; index < totalTargets; index++) {
            NodeRuntime targetNodeRuntime = targets.get(index);
            RuntimeMessage branchMessage = totalTargets <= 1 ? message : message.deepCopy();
            activeExecution.context().retainTask();

            FutureTask<Void> futureTask = new FutureTask<>(() -> {
                try {
                    executeNode(flowRuntime, activeExecution, targetNodeRuntime, branchMessage);
                    return null;
                } finally {
                    executionService.lifecycleManager().completeTask(flowRuntime, executionId);
                }
            }) {
                @Override
                protected void done() {
                    activeExecution.futures().remove(this);
                }
            };

            activeExecution.futures().add(futureTask);
            workerExecutor.execute(futureTask);
        }
    }

    private void executeNode(
            FlowRuntime flowRuntime,
            ActiveExecution activeExecution,
            NodeRuntime nodeRuntime,
            RuntimeMessage message) {
        ExecutionContext context = activeExecution.context();
        if (context.isCancellationRequested()) {
            return;
        }

        CompiledNode node = nodeRuntime.compiledNode();
        System.out.println("[NODE EXECUTOR] executeNode id: " + node.id() + " | category: " + node.category() + " | enabled: " + node.enabled());
        if (!node.enabled() || nodeController.isNodeDisabled(node.id())) {
            return;
        }

        nodeController.checkBreakpoint(node.id(), message);

        try {
            if (nexa.framework.runtime.domain.scripting.registry.PluginRegistry.hasPlugin(node.type())) {
                nexa.framework.runtime.api.plugin.NexaPlugin instance = nexa.framework.runtime.domain.scripting.registry.PluginRegistry
                        .getInstance(node.id());
                if (instance == null) {
                    throw new ValidationException("Plugin instance not found for node: " + node.id());
                }
                if (instance instanceof nexa.framework.runtime.api.plugin.NexaFunctionPlugin functionPlugin) {
                    RuntimeMessage result = functionPlugin.process(message);
                    nodeController.incrementProcessed(node.id());
                    if (result != null) {
                        submitNodeRoutes(flowRuntime, context.executionId(), node.id(), DEFAULT_PORT, result);
                    }
                } else if (instance instanceof nexa.framework.runtime.api.plugin.NexaSinkPlugin sinkPlugin) {
                    sinkPlugin.consume(message);
                    nodeController.incrementProcessed(node.id());
                }
                return;
            }

            if (node.category() == NodeCategory.EXECUTOR) {
                executeExecutorNode(flowRuntime, context, node, message);
                nodeController.incrementProcessed(node.id());
                return;
            }

            if (node.category() == NodeCategory.OUTPUT) {
                outputConsumer.consume(context, node.id(), message.deepCopy());
                nodeController.incrementProcessed(node.id());
                return;
            }

            throw new ValidationException("Input node " + node.id() + " cannot be downstream target");
        } catch (Throwable throwable) {
            nodeController.incrementErrors(node.id());
            System.err.println("[NODE EXECUTION ERROR][" + node.id() + "] "
                    + throwable.getClass().getSimpleName()
                    + ": " + throwable.getMessage());
            throwable.printStackTrace();

            int lineNumber = -1;
            Throwable cause = throwable;
            while (cause != null) {
                if (cause.getClass().getName().contains("NexaScriptException")) {
                    try {
                        java.lang.reflect.Method lineMethod = cause.getClass().getMethod("line");
                        lineNumber = (Integer) lineMethod.invoke(cause);
                    } catch (Exception ignored) {
                    }
                    break;
                }
                cause = cause.getCause();
            }

            eventBus.publish("nexa/monitor/node/errors", new nexa.framework.runtime.api.control.events.ScriptNodeFailureEvent(
                context.workspaceId(),
                context.flowId(),
                node.id(),
                lineNumber,
                throwable.getMessage() != null ? throwable.getMessage() : throwable.toString(),
                message,
                System.currentTimeMillis()
            ));

            if (!context.isCancellationRequested()) {
                context.markFailed(throwable);
                context.requestCancellation();
            }
        }
    }

    private void executeExecutorNode(
            FlowRuntime flowRuntime,
            ExecutionContext context,
            CompiledNode node,
            RuntimeMessage inputMessage) {
        if (node.compiledScript() == null) {
            throw new ValidationException("Executor node " + node.id() + " is missing compiled script");
        }

        ScriptRuntimeContext runtimeContext = new ScriptRuntimeContext(
                context.workspaceId(),
                context.flowId(),
                node.id(),
                context.executionId().toString(),
                context.createdAt(),
                context.deadline(),
                context.data());

        ScriptExecutionResult result = node.compiledScript().execute(inputMessage, runtimeContext);
        if (result.stopped()) {
            return;
        }

        for (Map.Entry<String, List<RuntimeMessage>> entry : result.emittedByPort().entrySet()) {
            String sourcePort = entry.getKey();
            for (RuntimeMessage emittedMessage : entry.getValue()) {
                submitNodeRoutes(flowRuntime, context.executionId(), node.id(), sourcePort, emittedMessage);
            }
        }
    }
}
