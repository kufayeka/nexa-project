package nexa.framework.runtime.domain.execution.service;

import nexa.framework.runtime.domain.execution.model.ActiveExecution;
import nexa.framework.runtime.domain.execution.model.FlowRuntime;
import nexa.framework.runtime.domain.execution.model.WorkspaceRuntime;
import nexa.framework.runtime.domain.deployment.model.CompiledConnection;
import nexa.framework.runtime.api.RuntimeConfiguration;
import nexa.framework.runtime.domain.deployment.model.CompiledNode;
import nexa.framework.runtime.domain.execution.model.ExecutionContext;
import nexa.framework.runtime.domain.execution.model.ExecutionStatus;
import nexa.framework.runtime.domain.scheduler.model.InputNodeRuntimeState;
import nexa.framework.runtime.api.model.RuntimeMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class ExecutionLifecycleManager {
    private static final String DEFAULT_PORT = "default";
    private final RuntimeConfiguration configuration;
    private final ScheduledExecutorService scheduler;
    private final RuntimeExecutionService executionService;

    ExecutionLifecycleManager(RuntimeConfiguration configuration, ScheduledExecutorService scheduler, RuntimeExecutionService executionService) {
        this.configuration = configuration;
        this.scheduler = scheduler;
        this.executionService = executionService;
    }

    void executeTriggeredInput(WorkspaceRuntime workspaceRuntime, FlowRuntime flowRuntime, CompiledNode inputNode, RuntimeMessage seedMessage) {
        InputNodeRuntimeState inputState = flowRuntime.inputStateByNodeId().computeIfAbsent(inputNode.id(), ignored -> new InputNodeRuntimeState(inputNode.inputPolicy().maxConcurrentExecutions()));
        if (!inputState.executionGate().tryAcquire()) {
            flowRuntime.statistics().incrementRejected();
            return;
        }
        Instant createdAt = Instant.now();
        ExecutionContext context = new ExecutionContext(workspaceRuntime.workspaceId(), flowRuntime.compiledFlow().flowId(), createdAt, createdAt.plus(configuration.maxExecutionLifetime()), workspaceRuntime.tagStore());
        flowRuntime.activeExecutions().put(context.executionId(), new ActiveExecution(context, inputNode.id()));
        flowRuntime.statistics().incrementRunning();
        context.retainTask();
        context.setTimeoutTask(scheduler.schedule(() -> cancelExecution(flowRuntime, context.executionId(), true), configuration.maxExecutionLifetime().toMillis(), TimeUnit.MILLISECONDS));
        executionService.nodeExecutor().submitNodeRoutes(flowRuntime, context.executionId(), inputNode.id(), DEFAULT_PORT, seedMessage);
        completeTask(flowRuntime, context.executionId());
    }

    void injectMessageIntoConnection(WorkspaceRuntime workspaceRuntime, FlowRuntime flowRuntime,
            CompiledConnection connection, RuntimeMessage message) {
        if (!workspaceRuntime.enabled() || !connection.enabled()) return;
        if (!flowRuntime.compiledFlow().connection(connection.id()).enabled()) return;

        Instant createdAt = Instant.now();
        ExecutionContext context = new ExecutionContext(workspaceRuntime.workspaceId(), flowRuntime.compiledFlow().flowId(), createdAt, createdAt.plus(configuration.maxExecutionLifetime()), workspaceRuntime.tagStore());
        flowRuntime.activeExecutions().put(context.executionId(), new ActiveExecution(context, connection.sourceNodeId()));
        flowRuntime.statistics().incrementRunning();
        context.retainTask();
        context.setTimeoutTask(scheduler.schedule(() -> cancelExecution(flowRuntime, context.executionId(), true), configuration.maxExecutionLifetime().toMillis(), TimeUnit.MILLISECONDS));

        executionService.nodeExecutor().submitConnectionTarget(flowRuntime, context.executionId(), connection, message == null ? new RuntimeMessage() : message.deepCopy());
        completeTask(flowRuntime, context.executionId());
    }

    void completeTask(FlowRuntime flowRuntime, UUID executionId) {
        ActiveExecution activeExecution = flowRuntime.activeExecutions().get(executionId);
        if (activeExecution == null) return;
        if (activeExecution.context().releaseTask() > 0) return;
        finalizeExecution(flowRuntime, activeExecution);
    }

    void finalizeExecution(FlowRuntime flowRuntime, ActiveExecution activeExecution) {
        ExecutionContext context = activeExecution.context();
        if (context.status() == ExecutionStatus.RUNNING) {
            if (context.isCancellationRequested()) context.markCancelled(); else context.markCompleted();
        }
        if (context.timeoutTask() != null) context.timeoutTask().cancel(false);
        if (context.status() == ExecutionStatus.FAILED) flowRuntime.statistics().incrementFailed();
        else if (context.status() == ExecutionStatus.CANCELLED || context.status() == ExecutionStatus.TIMED_OUT) flowRuntime.statistics().incrementCancelled();
        else if (context.status() == ExecutionStatus.COMPLETED) flowRuntime.statistics().incrementCompleted();
        flowRuntime.statistics().addDurationNanos(Duration.between(context.createdAt(), Instant.now()).toNanos());
        flowRuntime.statistics().decrementRunning();
        InputNodeRuntimeState inputState = flowRuntime.inputStateByNodeId().get(activeExecution.inputNodeId());
        if (inputState != null) inputState.executionGate().release();
        flowRuntime.activeExecutions().remove(context.executionId());
        for (Future<?> future : snapshotFutures(activeExecution.futures())) if (!future.isDone()) future.cancel(true);
        context.cleanup();
    }

    void cancelExecution(FlowRuntime flowRuntime, UUID executionId, boolean timeoutTriggered) {
        ActiveExecution activeExecution = flowRuntime.activeExecutions().get(executionId);
        if (activeExecution == null) return;
        ExecutionContext context = activeExecution.context();
        if (!context.requestCancellation()) return;
        if (timeoutTriggered) context.markTimedOut(); else context.markCancelled();
        for (Future<?> future : snapshotFutures(activeExecution.futures())) if (!future.isDone()) future.cancel(true);
    }

    void stopWorkspaceRuntime(WorkspaceRuntime workspaceRuntime) {
        for (FlowRuntime flowRuntime : workspaceRuntime.flowsById().values()) {
            for (InputNodeRuntimeState inputState : flowRuntime.inputStateByNodeId().values()) inputState.cancelAllScheduledTriggers();
            for (UUID executionId : flowRuntime.activeExecutions().keySet()) cancelExecution(flowRuntime, executionId, false);
        }
    }

    private List<Future<?>> snapshotFutures(List<Future<?>> futures) {
        synchronized (futures) { return new ArrayList<>(futures); }
    }

    void injectMessage(WorkspaceRuntime workspaceRuntime, FlowRuntime flowRuntime, String sourceNodeId, RuntimeMessage message) {
        Instant createdAt = Instant.now();
        ExecutionContext context = new ExecutionContext(workspaceRuntime.workspaceId(), flowRuntime.compiledFlow().flowId(), createdAt, createdAt.plus(configuration.maxExecutionLifetime()), workspaceRuntime.tagStore());
        flowRuntime.activeExecutions().put(context.executionId(), new ActiveExecution(context, sourceNodeId));
        flowRuntime.statistics().incrementRunning();
        context.retainTask();
        context.setTimeoutTask(scheduler.schedule(() -> cancelExecution(flowRuntime, context.executionId(), true), configuration.maxExecutionLifetime().toMillis(), TimeUnit.MILLISECONDS));
        executionService.nodeExecutor().submitNodeRoutes(flowRuntime, context.executionId(), sourceNodeId, DEFAULT_PORT, message);
        completeTask(flowRuntime, context.executionId());
    }
}
