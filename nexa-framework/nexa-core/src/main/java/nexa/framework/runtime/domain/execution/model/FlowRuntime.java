package nexa.framework.runtime.domain.execution.model;

import nexa.framework.runtime.domain.deployment.model.CompiledConnection;
import nexa.framework.runtime.domain.deployment.model.CompiledFlow;
import nexa.framework.runtime.domain.deployment.model.CompiledNode;
import nexa.framework.runtime.domain.scheduler.model.InputNodeRuntimeState;
import nexa.framework.runtime.domain.statistics.service.FlowStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FlowRuntime {

    private final CompiledFlow compiledFlow;
    private final FlowStatistics statistics;
    private final ConcurrentMap<String, InputNodeRuntimeState> inputStateByNodeId;
    private final ConcurrentMap<UUID, ActiveExecution> activeExecutions;
    private final ConcurrentMap<String, NodeRuntime> nodeRuntimeById;
    private final ConcurrentMap<String, ConcurrentMap<String, List<NodeRuntime>>> targetsByNodeAndPort;

    public FlowRuntime(String workspaceId, CompiledFlow compiledFlow) {
        this.compiledFlow = compiledFlow;
        this.statistics = new FlowStatistics(workspaceId, compiledFlow.flowId());
        this.inputStateByNodeId = new ConcurrentHashMap<>();
        this.activeExecutions = new ConcurrentHashMap<>();
        this.nodeRuntimeById = new ConcurrentHashMap<>();
        this.targetsByNodeAndPort = new ConcurrentHashMap<>();

        for (CompiledNode node : compiledFlow.nodeById().values()) {
            nodeRuntimeById.put(node.id(), new NodeRuntime(node));
        }

        refreshRoutes();
    }

    public CompiledFlow compiledFlow() {
        return compiledFlow;
    }

    public FlowStatistics statistics() {
        return statistics;
    }

    public ConcurrentMap<String, InputNodeRuntimeState> inputStateByNodeId() {
        return inputStateByNodeId;
    }

    public ConcurrentMap<UUID, ActiveExecution> activeExecutions() {
        return activeExecutions;
    }

    public NodeRuntime nodeRuntime(String nodeId) {
        return nodeRuntimeById.get(nodeId);
    }

    public List<NodeRuntime> targets(String sourceNodeId, String sourcePort) {
        Map<String, List<NodeRuntime>> byPort = targetsByNodeAndPort.get(sourceNodeId);
        if (byPort == null) {
            return List.of();
        }

        List<NodeRuntime> targets = byPort.get(sourcePort);
        if (targets != null) {
            return targets;
        }

        return byPort.getOrDefault("default", List.of());
    }

    public boolean isRouteEnabled(String sourceNodeId, String sourcePort, String targetNodeId) {
        for (CompiledConnection connection : compiledFlow.connectionById().values()) {
            if (connection.sourceNodeId().equals(sourceNodeId)
                    && connection.sourcePort().equals(sourcePort)
                    && connection.targetNodeId().equals(targetNodeId)) {
                return connection.enabled();
            }
        }
        return false;
    }

    public void refreshRoutes() {
        ConcurrentMap<String, ConcurrentMap<String, List<NodeRuntime>>> refreshed = new ConcurrentHashMap<>();

        for (Map.Entry<String, Map<String, List<String>>> sourceEntry : compiledFlow.routesByNodeAndPort().entrySet()) {
            ConcurrentMap<String, List<NodeRuntime>> resolvedByPort = new ConcurrentHashMap<>();

            for (Map.Entry<String, List<String>> portEntry : sourceEntry.getValue().entrySet()) {
                List<NodeRuntime> resolvedTargets = new ArrayList<>();
                for (String targetNodeId : portEntry.getValue()) {
                    NodeRuntime targetRuntime = nodeRuntimeById.get(targetNodeId);
                    if (targetRuntime == null) {
                        throw new IllegalStateException(
                                "Target node " + targetNodeId + " is not available in flow " + compiledFlow.flowId());
                    }
                    resolvedTargets.add(targetRuntime);
                }
                resolvedByPort.put(portEntry.getKey(), new CopyOnWriteArrayList<>(resolvedTargets));
            }

            refreshed.put(sourceEntry.getKey(), resolvedByPort);
        }

        targetsByNodeAndPort.clear();
        targetsByNodeAndPort.putAll(refreshed);
    }

    public void addRoute(String sourceNodeId, String sourcePort, NodeRuntime targetRuntime) {
        targetsByNodeAndPort.computeIfAbsent(sourceNodeId, k -> new ConcurrentHashMap<>())
                .compute(sourcePort, (port, list) -> {
                    List<NodeRuntime> newList = list == null
                            ? new CopyOnWriteArrayList<>()
                            : new CopyOnWriteArrayList<>(list);
                    if (!newList.contains(targetRuntime)) {
                        newList.add(targetRuntime);
                    }
                    return newList;
                });
    }

    public void removeRoute(String sourceNodeId, String sourcePort, String targetNodeId) {
        ConcurrentMap<String, List<NodeRuntime>> byPort = targetsByNodeAndPort.get(sourceNodeId);
        if (byPort != null) {
            byPort.computeIfPresent(sourcePort, (port, list) -> {
                List<NodeRuntime> newList = new CopyOnWriteArrayList<>(list);
                newList.removeIf(node -> node.compiledNode().id().equals(targetNodeId));
                return newList;
            });
        }
    }

    public boolean removeConnection(String connectionId) {
        var connection = compiledFlow.connection(connectionId);
        if (connection == null) {
            return false;
        }

        removeRoute(connection.sourceNodeId(), connection.sourcePort(), connection.targetNodeId());
        return true;
    }

    public void refreshNodeRuntime(String nodeId) {
        CompiledNode updatedNode = compiledFlow.node(nodeId);
        NodeRuntime nodeRuntime = nodeRuntimeById.get(nodeId);
        if (updatedNode == null || nodeRuntime == null) {
            return;
        }
        nodeRuntime.setCompiledNode(updatedNode);
    }
}
