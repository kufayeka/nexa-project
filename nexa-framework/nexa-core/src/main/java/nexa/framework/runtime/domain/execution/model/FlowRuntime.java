package nexa.framework.runtime.domain.execution.model;

import nexa.framework.runtime.domain.deployment.model.CompiledFlow;
import nexa.framework.runtime.domain.deployment.model.CompiledNode;
import nexa.framework.runtime.domain.scheduler.model.InputNodeRuntimeState;
import nexa.framework.runtime.domain.statistics.service.FlowStatistics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class FlowRuntime {

    private final CompiledFlow compiledFlow;
    private final FlowStatistics statistics;
    private final ConcurrentMap<String, InputNodeRuntimeState> inputStateByNodeId;
    private final ConcurrentMap<UUID, ActiveExecution> activeExecutions;
    private final ConcurrentMap<String, NodeRuntime> nodeRuntimeById;
    private final Map<String, Map<String, List<NodeRuntime>>> targetsByNodeAndPort;

    public FlowRuntime(String workspaceId, CompiledFlow compiledFlow) {
        this.compiledFlow = compiledFlow;
        this.statistics = new FlowStatistics(workspaceId, compiledFlow.flowId());
        this.inputStateByNodeId = new ConcurrentHashMap<>();
        this.activeExecutions = new ConcurrentHashMap<>();
        this.nodeRuntimeById = new ConcurrentHashMap<>();

        for (CompiledNode node : compiledFlow.nodeById().values()) {
            nodeRuntimeById.put(node.id(), new NodeRuntime(node));
        }

        Map<String, Map<String, List<NodeRuntime>>> resolvedTargets = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> sourceEntry : compiledFlow.routesByNodeAndPort().entrySet()) {
            Map<String, List<NodeRuntime>> resolvedByPort = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> portEntry : sourceEntry.getValue().entrySet()) {
                List<NodeRuntime> resolvedTargetsForPort = new ArrayList<>();
                for (String targetNodeId : portEntry.getValue()) {
                    NodeRuntime targetRuntime = nodeRuntimeById.get(targetNodeId);
                    if (targetRuntime == null) {
                        throw new IllegalStateException(
                                "Target node " + targetNodeId + " is not available in flow " + compiledFlow.flowId());
                    }
                    resolvedTargetsForPort.add(targetRuntime);
                }
                resolvedByPort.put(portEntry.getKey(), List.copyOf(resolvedTargetsForPort));
            }
            resolvedTargets.put(sourceEntry.getKey(), Map.copyOf(resolvedByPort));
        }
        this.targetsByNodeAndPort = Map.copyOf(resolvedTargets);
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

    public void refreshNodeRuntime(String nodeId) {
        CompiledNode updatedNode = compiledFlow.node(nodeId);
        NodeRuntime nodeRuntime = nodeRuntimeById.get(nodeId);
        if (updatedNode == null || nodeRuntime == null) {
            return;
        }
        nodeRuntime.setCompiledNode(updatedNode);
    }
}


