package nexa.framework.runtime.domain.execution.model;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExecutionContext {

    private final UUID executionId;
    private final String workspaceId;
    private final String flowId;
    private final Instant createdAt;
    private final Instant deadline;
    private final ConcurrentMap<String, Object> data;
    private final AtomicBoolean cancellationRequested;
    private final AtomicInteger activeTaskCounter;
    private final nexa.framework.runtime.api.state.TypedTagStore tagStore;
    private volatile Throwable failure;
    private volatile ExecutionStatus status;
    private volatile ScheduledFuture<?> timeoutTask;

    public ExecutionContext(String workspaceId, String flowId, Instant createdAt, Instant deadline, nexa.framework.runtime.api.state.TypedTagStore tagStore) {
        this.executionId = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.flowId = flowId;
        this.createdAt = createdAt;
        this.deadline = deadline;
        this.tagStore = tagStore;
        this.data = new ConcurrentHashMap<>();
        this.cancellationRequested = new AtomicBoolean(false);
        this.activeTaskCounter = new AtomicInteger(0);
        this.status = ExecutionStatus.RUNNING;
    }

    public nexa.framework.runtime.api.state.TypedTagStore tagStore() {
        return tagStore;
    }

    public UUID executionId() {
        return executionId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String flowId() {
        return flowId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant deadline() {
        return deadline;
    }

    public ConcurrentMap<String, Object> data() {
        return data;
    }

    public ExecutionStatus status() {
        return status;
    }

    public Throwable failure() {
        return failure;
    }

    public boolean isCancellationRequested() {
        return cancellationRequested.get();
    }

    public boolean requestCancellation() {
        return cancellationRequested.compareAndSet(false, true);
    }

    public int retainTask() {
        return activeTaskCounter.incrementAndGet();
    }

    public int releaseTask() {
        return activeTaskCounter.decrementAndGet();
    }

    public int activeTaskCount() {
        return activeTaskCounter.get();
    }

    public void markCompleted() {
        status = ExecutionStatus.COMPLETED;
    }

    public void markFailed(Throwable throwable) {
        this.failure = throwable;
        status = ExecutionStatus.FAILED;
    }

    public void markCancelled() {
        status = ExecutionStatus.CANCELLED;
    }

    public void markTimedOut() {
        status = ExecutionStatus.TIMED_OUT;
    }

    public void setTimeoutTask(ScheduledFuture<?> timeoutTask) {
        this.timeoutTask = timeoutTask;
    }

    public ScheduledFuture<?> timeoutTask() {
        return timeoutTask;
    }

    public void cleanup() {
        data.clear();
        failure = null;
    }
}


