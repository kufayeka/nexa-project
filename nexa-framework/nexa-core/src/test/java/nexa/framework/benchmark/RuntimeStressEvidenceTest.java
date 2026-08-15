package nexa.framework.benchmark;

import nexa.framework.runtime.api.OutputConsumer;
import nexa.framework.runtime.api.RuntimeConfiguration;
import nexa.framework.runtime.api.RuntimeEngine;
import nexa.framework.runtime.domain.execution.service.DefaultRuntimeEngine;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.statistics.model.RuntimeStatisticsSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeStressEvidenceTest {

    @Test
    void evidence_parallel_virtual_thread_and_routing() throws Exception {
        int branches = 8;

        AtomicInteger activeOutputs = new AtomicInteger(0);
        AtomicInteger maxActiveOutputs = new AtomicInteger(0);
        AtomicBoolean virtualThreadSeen = new AtomicBoolean(false);

        CountDownLatch enteredOutput = new CountDownLatch(branches);
        CountDownLatch allDone = new CountDownLatch(branches);
        CountDownLatch releaseOutputs = new CountDownLatch(1);

        OutputConsumer outputConsumer = (context, nodeId, message) -> {
            if (!nodeId.startsWith("out-")) {
                return;
            }

            if (Thread.currentThread().isVirtual()) {
                virtualThreadSeen.set(true);
            }

            int current = activeOutputs.incrementAndGet();
            maxActiveOutputs.accumulateAndGet(current, Math::max);
            enteredOutput.countDown();

            try {
                releaseOutputs.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                activeOutputs.decrementAndGet();
                allDone.countDown();
            }
        };

        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(5)),
                outputConsumer);

        String workspaceId = "ws-parallel-evidence";
        String flowId = "flow-parallel-evidence";

        runtime.deploy(StressWorkspaceGenerator.parallelManualWorkspace(workspaceId, flowId, branches));
        runtime.startRuntime();

        runtime.trigger(workspaceId, flowId, "input-manual", new RuntimeMessage());

        assertTrue(enteredOutput.await(3, TimeUnit.SECONDS));
        releaseOutputs.countDown();
        assertTrue(allDone.await(3, TimeUnit.SECONDS));

        assertTrue(maxActiveOutputs.get() > 1);
        assertTrue(virtualThreadSeen.get());

        waitUntil(() -> runtime.statistics(workspaceId, flowId).completed() >= 1, Duration.ofSeconds(2));

        RuntimeStatisticsSnapshot stats = runtime.statistics(workspaceId, flowId);
        assertTrue(stats.failed() == 0);
        assertTrue(stats.running() == 0);

        System.out.println("[evidence] parallel-max-active=" + maxActiveOutputs.get()
                + " virtual-thread-seen=" + virtualThreadSeen.get()
                + " completed=" + stats.completed());

        runtime.stopRuntime();
    }

    @Test
    void evidence_scheduler_runs_without_manual_trigger() throws Exception {
        AtomicInteger outputCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        OutputConsumer outputConsumer = (context, nodeId, message) -> {
            if (!"out-main".equals(nodeId)) {
                return;
            }
            outputCount.incrementAndGet();
            latch.countDown();
        };

        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(5)),
                outputConsumer);

        String workspaceId = "ws-scheduler-evidence";
        String flowId = "flow-scheduler-evidence";

        runtime.deploy(StressWorkspaceGenerator.timedWorkspace(workspaceId, flowId, "100ms"));
        runtime.startRuntime();

        assertTrue(latch.await(4, TimeUnit.SECONDS));
        Thread.sleep(100);

        RuntimeStatisticsSnapshot stats = runtime.statistics(workspaceId, flowId);
        assertTrue(stats.completed() >= 3);

        System.out.println("[evidence] scheduler-output-count=" + outputCount.get()
                + " completed=" + stats.completed()
                + " avgMs=" + stats.averageExecutionTimeMillis());

        runtime.stopRuntime();
    }

    @Test
    void evidence_execution_thread_and_memory_cleanup_under_stress() throws Exception {
        int triggerCount = 400;
        int branches = 4;
        int expectedOutputs = triggerCount * branches;

        long usedHeapBefore = usedHeapBytesAfterGc();
        int workerThreadsBefore = countLiveThreads("nexa-worker-");

        CountDownLatch outputsLatch = new CountDownLatch(expectedOutputs);
        AtomicInteger outputCounter = new AtomicInteger(0);

        OutputConsumer outputConsumer = (context, nodeId, message) -> {
            if (!nodeId.startsWith("out-")) {
                return;
            }
            outputCounter.incrementAndGet();
            outputsLatch.countDown();
        };

        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(10)),
                outputConsumer);

        String workspaceId = "ws-cleanup-evidence";
        String flowId = "flow-cleanup-evidence";

        runtime.deploy(StressWorkspaceGenerator.parallelManualWorkspace(workspaceId, flowId, branches));
        runtime.startRuntime();

        for (int index = 0; index < triggerCount; index++) {
            runtime.trigger(workspaceId, flowId, "input-manual", new RuntimeMessage());
        }

        assertTrue(outputsLatch.await(20, TimeUnit.SECONDS));
        waitUntil(() -> {
            RuntimeStatisticsSnapshot snapshot = runtime.statistics(workspaceId, flowId);
            long accounted = snapshot.completed() + snapshot.failed() + snapshot.cancelled() + snapshot.rejected();
            return snapshot.running() == 0 && accounted >= triggerCount;
        }, Duration.ofSeconds(10));

        RuntimeStatisticsSnapshot stats = runtime.statistics(workspaceId, flowId);
        assertTrue(stats.completed() >= triggerCount);
        assertTrue(stats.rejected() == 0);
        assertTrue(stats.failed() == 0);
        assertTrue(stats.cancelled() == 0);

        runtime.stopRuntime();

        Thread.sleep(300);

        long usedHeapAfter = usedHeapBytesAfterGc();
        int workerThreadsAfter = countLiveThreads("nexa-worker-");

        long heapGrowthBytes = usedHeapAfter - usedHeapBefore;

        assertTrue(workerThreadsAfter <= workerThreadsBefore + 2);
        assertTrue(heapGrowthBytes < 64L * 1024L * 1024L);

        System.out.println("[evidence] outputs=" + outputCounter.get()
                + " completed=" + stats.completed()
                + " running=" + stats.running()
                + " workerThreadsBefore=" + workerThreadsBefore
                + " workerThreadsAfter=" + workerThreadsAfter
                + " heapGrowthBytes=" + heapGrowthBytes);
    }

    @Test
    void evidence_incremental_deploy_compiles_only_changed_flow() {
        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(15)),
                (context, nodeId, message) -> {
                });

        String workspaceId = "ws-incremental-evidence";
        int flowCount = 120;

        long firstDeployStarted = System.nanoTime();
        runtime.deploy(StressWorkspaceGenerator.largeWorkspaceForIncrementalDeploy(workspaceId, flowCount, -1));
        long firstDeployMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - firstDeployStarted);

        long secondDeployStarted = System.nanoTime();
        runtime.deploy(StressWorkspaceGenerator.largeWorkspaceForIncrementalDeploy(workspaceId, flowCount, 137));
        long secondDeployMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - secondDeployStarted);

        assertTrue(secondDeployMs < firstDeployMs);

        runtime.stopRuntime();

        System.out.println("[evidence] incremental-first-deploy-ms=" + firstDeployMs
                + " incremental-second-deploy-ms=" + secondDeployMs
                + " flows=" + flowCount);
    }

    private void waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }

        assertTrue(condition.getAsBoolean());
    }

    private long usedHeapBytesAfterGc() throws InterruptedException {
        Runtime runtime = Runtime.getRuntime();

        for (int index = 0; index < 3; index++) {
            System.gc();
            Thread.sleep(50);
        }

        return runtime.totalMemory() - runtime.freeMemory();
    }

    private int countLiveThreads(String prefix) {
        int count = 0;

        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith(prefix)) {
                count++;
            }
        }

        return count;
    }
}

