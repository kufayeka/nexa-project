package nexa.framework.runtime.domain.scheduler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public final class InputNodeRuntimeState {

    private final Semaphore executionGate;
    private final List<ScheduledFuture<?>> scheduledTriggers;
    private final AtomicLong tickCounter;

    public InputNodeRuntimeState(int maxConcurrentExecutions) {
        this.executionGate = new Semaphore(maxConcurrentExecutions);
        this.scheduledTriggers = Collections.synchronizedList(new ArrayList<>());
        this.tickCounter = new AtomicLong(0);
    }

    public Semaphore executionGate() {
        return executionGate;
    }

    public boolean hasScheduledTrigger() {
        synchronized (scheduledTriggers) {
            return !scheduledTriggers.isEmpty();
        }
    }

    public void addScheduledTrigger(ScheduledFuture<?> scheduledFuture) {
        scheduledTriggers.add(scheduledFuture);
    }

    public void cancelAllScheduledTriggers() {
        List<ScheduledFuture<?>> snapshot;
        synchronized (scheduledTriggers) {
            snapshot = new ArrayList<>(scheduledTriggers);
            scheduledTriggers.clear();
        }

        for (ScheduledFuture<?> scheduledFuture : snapshot) {
            scheduledFuture.cancel(false);
        }
    }

    public long nextTickCount() {
        return tickCounter.incrementAndGet();
    }
}


