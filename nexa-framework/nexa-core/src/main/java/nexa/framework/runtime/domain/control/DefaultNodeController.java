package nexa.framework.runtime.domain.control;

import nexa.framework.runtime.api.control.NodeControl;
import nexa.framework.runtime.api.control.model.NodeInfo;
import nexa.framework.runtime.api.control.model.NodeMessageHistory;
import nexa.framework.runtime.api.model.RuntimeMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultNodeController implements NodeControl {
    private final Set<String> disabledNodes = ConcurrentHashMap.newKeySet();
    private final Set<String> breakpoints = ConcurrentHashMap.newKeySet();
    private final Map<String, RuntimeMessage> pausedMessages = new ConcurrentHashMap<>();
    private final Map<String, LockAndCondition> pauseLocks = new ConcurrentHashMap<>();
    private final Set<String> stepFlags = ConcurrentHashMap.newKeySet();

    private record LockAndCondition(ReentrantLock lock, Condition condition) {
    }

    public boolean isNodeDisabled(String nodeId) {
        return disabledNodes.contains(nodeId);
    }

    public void checkBreakpoint(String nodeId, RuntimeMessage message) {
        if (breakpoints.contains(nodeId)) {
            pausedMessages.put(nodeId, message);
            LockAndCondition lac = pauseLocks.computeIfAbsent(nodeId, k -> {
                ReentrantLock lock = new ReentrantLock();
                return new LockAndCondition(lock, lock.newCondition());
            });

            lac.lock().lock();
            try {
                while (breakpoints.contains(nodeId) && !stepFlags.remove(nodeId)) {
                    lac.condition().await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pausedMessages.remove(nodeId);
                lac.lock().unlock();
            }
        }
    }

    @Override
    public void enableNode(String nodeId) {
        disabledNodes.remove(nodeId);
    }

    @Override
    public void disableNode(String nodeId) {
        disabledNodes.add(nodeId);
    }

    @Override
    public void addBreakpoint(String nodeId) {
        breakpoints.add(nodeId);
    }

    @Override
    public void removeBreakpoint(String nodeId) {
        breakpoints.remove(nodeId);
        resumeNode(nodeId);
    }

    @Override
    public void resumeNode(String nodeId) {
        LockAndCondition lac = pauseLocks.get(nodeId);
        if (lac != null) {
            lac.lock().lock();
            try {
                lac.condition().signalAll();
            } finally {
                lac.lock().unlock();
            }
        }
    }

    @Override
    public void stepNode(String nodeId) {
        stepFlags.add(nodeId);
        resumeNode(nodeId);
    }

    private final Map<String, java.util.concurrent.atomic.LongAdder> processedCounters = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.atomic.LongAdder> errorCounters = new ConcurrentHashMap<>();

    public void incrementProcessed(String nodeId) {
        processedCounters.computeIfAbsent(nodeId, k -> new java.util.concurrent.atomic.LongAdder()).increment();
    }

    public void incrementErrors(String nodeId) {
        errorCounters.computeIfAbsent(nodeId, k -> new java.util.concurrent.atomic.LongAdder()).increment();
    }

    public void resetNodeMetrics(String nodeId) {
        processedCounters.remove(nodeId);
        errorCounters.remove(nodeId);
    }

    @Override
    public RuntimeMessage getPausedMessage(String nodeId) {
        return pausedMessages.get(nodeId);
    }

    @Override
    public NodeInfo getNodeInfo(String nodeId) {
        long processedCount = processedCounters.containsKey(nodeId) ? processedCounters.get(nodeId).sum() : 0L;
        long errorCount = errorCounters.containsKey(nodeId) ? errorCounters.get(nodeId).sum() : 0L;
        return new NodeInfo(
                nodeId, "default-flow", "generic",
                !disabledNodes.contains(nodeId),
                breakpoints.contains(nodeId),
                pausedMessages.containsKey(nodeId),
                processedCount, errorCount);
    }

    @Override
    public NodeMessageHistory getNodeMessages(String nodeId) {
        return new NodeMessageHistory(nodeId, List.of(), List.of());
    }
}