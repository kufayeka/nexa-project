package nexa.framework.runtime.domain.deployment.model;

import nexa.framework.runtime.domain.deployment.exception.ValidationException;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompiledFlow {

    private final String flowId;
    private final String flowName;
    private final boolean enabled;
    private final Map<String, CompiledNode> nodeById;
    private final Map<String, Map<String, List<String>>> routesByNodeAndPort;

    public CompiledFlow(
            String flowId,
            String flowName,
            boolean enabled,
            Map<String, CompiledNode> nodeById,
            Map<String, Map<String, List<String>>> routesByNodeAndPort) {
        this.flowId = flowId;
        this.flowName = flowName;
        this.enabled = enabled;
        this.nodeById = new ConcurrentHashMap<>(new LinkedHashMap<>(nodeById));
        this.routesByNodeAndPort = Collections.unmodifiableMap(new LinkedHashMap<>(routesByNodeAndPort));
    }

    public String flowId() {
        return flowId;
    }

    public String flowName() {
        return flowName;
    }

    public boolean enabled() {
        return enabled;
    }

    public Map<String, CompiledNode> nodeById() {
        return nodeById;
    }

    public Map<String, Map<String, List<String>>> routesByNodeAndPort() {
        return routesByNodeAndPort;
    }

    public List<String> inputNodeIds() {
        java.util.ArrayList<String> inputIds = new java.util.ArrayList<>();
        for (CompiledNode node : nodeById.values()) {
            if (node.category() == nexa.framework.runtime.domain.workspace.model.NodeCategory.INPUT) {
                inputIds.add(node.id());
            }
        }
        return List.copyOf(inputIds);
    }

    public List<String> targets(String sourceNodeId, String sourcePort) {
        Map<String, List<String>> byPort = routesByNodeAndPort.get(sourceNodeId);
        if (byPort == null) {
            return List.of();
        }

        List<String> targets = byPort.get(sourcePort);
        if (targets != null) {
            return targets;
        }

        return byPort.getOrDefault("default", List.of());
    }

    public CompiledNode node(String nodeId) {
        return nodeById.get(nodeId);
    }

    public void setNodeEnabled(String nodeId, boolean enabled) {
        CompiledNode node = nodeById.get(nodeId);
        if (node == null) {
            throw new ValidationException("Unknown node id " + nodeId + " in flow " + flowId);
        }

        nodeById.put(nodeId, new CompiledNode(
                node.id(),
                node.category(),
                node.type(),
                enabled,
                node.inputPolicy(),
                node.config(),
                node.language(),
                node.compiledScript()));
    }
}



