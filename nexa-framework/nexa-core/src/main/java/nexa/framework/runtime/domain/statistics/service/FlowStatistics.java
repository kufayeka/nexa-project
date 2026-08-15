package nexa.framework.runtime.domain.statistics.service;

import nexa.framework.runtime.domain.statistics.model.RuntimeStatisticsSnapshot;

import java.util.concurrent.atomic.LongAdder;

public final class FlowStatistics {

    private final String workspaceId;
    private final String flowId;
    private final LongAdder running = new LongAdder();
    private final LongAdder waiting = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder cancelled = new LongAdder();
    private final LongAdder completed = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder totalDurationNanos = new LongAdder();

    public FlowStatistics(String workspaceId, String flowId) {
        this.workspaceId = workspaceId;
        this.flowId = flowId;
    }

    public void incrementRunning() {
        running.increment();
    }

    public void decrementRunning() {
        running.decrement();
    }

    public void incrementWaiting() {
        waiting.increment();
    }

    public void decrementWaiting() {
        waiting.decrement();
    }

    public void incrementFailed() {
        failed.increment();
    }

    public void incrementCancelled() {
        cancelled.increment();
    }

    public void incrementCompleted() {
        completed.increment();
    }

    public void incrementRejected() {
        rejected.increment();
    }

    public void addDurationNanos(long nanos) {
        totalDurationNanos.add(nanos);
    }

    public RuntimeStatisticsSnapshot snapshot() {
        long completedValue = completed.sum();
        long totalMillisDivisor = completedValue == 0 ? 0 : completedValue;
        double averageMs = totalMillisDivisor == 0
                ? 0.0d
                : (double) totalDurationNanos.sum() / totalMillisDivisor / 1_000_000.0d;

        return new RuntimeStatisticsSnapshot(
                workspaceId,
                flowId,
                running.sum(),
                waiting.sum(),
                failed.sum(),
                cancelled.sum(),
                completed.sum(),
                rejected.sum(),
                averageMs);
    }

    public void reset() {
        running.reset();
        waiting.reset();
        failed.reset();
        cancelled.reset();
        completed.reset();
        rejected.reset();
        totalDurationNanos.reset();
    }
}


