package nexa.framework.runtime.domain.deployment.model;

import nexa.framework.runtime.domain.deployment.exception.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CompiledFlow {

    private final String flowId;
    private final String flowName;
    private final boolean enabled;
    private final Map<String, CompiledNode> nodeById;
    private volatile Map<String, Map<String, List<String>>> routesByNodeAndPort;
    private final Map<String, CompiledConnection> connectionById;

    public CompiledFlow(
            String flowId,
            String flowName,
            boolean enabled,
            Map<String, CompiledNode> nodeById,
            Map<String, Map<String, List<String>>> routesByNodeAndPort,
            Map<String, CompiledConnection> connectionById) {

        this.flowId = flowId;
        this.flowName = flowName;
        this.enabled = enabled;
        this.nodeById = new ConcurrentHashMap<>(new LinkedHashMap<>(nodeById));
        this.connectionById = new ConcurrentHashMap<>(new LinkedHashMap<>(connectionById));
        this.routesByNodeAndPort = immutableRoutes(routesByNodeAndPort);
    }

    public String flowId() { return flowId; }
    public String flowName() { return flowName; }
    public boolean enabled() { return enabled; }
    public Map<String, CompiledNode> nodeById() { return nodeById; }
    public Map<String, Map<String, List<String>>> routesByNodeAndPort() { return routesByNodeAndPort; }
    public Map<String, CompiledConnection> connectionById() { return Collections.unmodifiableMap(connectionById); }
    public CompiledConnection connection(String connectionId) { return connectionById.get(connectionId); }

    public List<String> inputNodeIds() {
        ArrayList<String> inputIds = new ArrayList<>();
        for (CompiledNode node : nodeById.values()) {
            if (node.category() == nexa.framework.runtime.domain.workspace.model.NodeCategory.INPUT) {
                inputIds.add(node.id());
            }
        }
        return List.copyOf(inputIds);
    }

    public List<String> targets(String sourceNodeId, String sourcePort) {
        Map<String, List<String>> byPort = routesByNodeAndPort.get(sourceNodeId);
        if (byPort == null) return List.of();
        List<String> targets = byPort.get(sourcePort);
        return targets != null ? targets : byPort.getOrDefault("default", List.of());
    }

    public boolean connectionEnabled(String sourceNodeId, String sourcePort, String targetNodeId) {
        for (CompiledConnection connection : connectionById.values()) {
            if (connection.sourceNodeId().equals(sourceNodeId)
                    && connection.sourcePort().equals(sourcePort)
                    && connection.targetNodeId().equals(targetNodeId)
                    && connection.enabled()) {
                return true;
            }
        }
        return false;
    }

    public CompiledNode node(String nodeId) { return nodeById.get(nodeId); }

    public void setNodeEnabled(String nodeId, boolean enabled) {
        CompiledNode node = nodeById.get(nodeId);
        if (node == null) {
            throw new ValidationException("Unknown node id " + nodeId + " in flow " + flowId);
        }
        nodeById.put(nodeId, new CompiledNode(
                node.id(), node.category(), node.type(), enabled,
                node.inputPolicy(), node.config(), node.language(), node.compiledScript()));
    }

    public synchronized void setConnectionEnabled(String connectionId, boolean enabled) {
        CompiledConnection connection = connectionById.get(connectionId);
        if (connection == null) {
            throw new ValidationException("Unknown connection id " + connectionId + " in flow " + flowId);
        }
        connection.setEnabled(enabled);
        rebuildRoutes();
    }

    private synchronized void rebuildRoutes() {
        Map<String, Map<String, List<String>>> rebuilt = new LinkedHashMap<>();
        for (String nodeId : nodeById.keySet()) {
            rebuilt.put(nodeId, new LinkedHashMap<>());
        }

        for (CompiledConnection connection : connectionById.values()) {
            if (!connection.enabled()) continue;
            Map<String, List<String>> byPort = rebuilt.get(connection.sourceNodeId());
            if (byPort == null) continue;
            byPort.computeIfAbsent(connection.sourcePort(), ignored -> new ArrayList<>())
                    .add(connection.targetNodeId());
        }

        routesByNodeAndPort = immutableRoutes(rebuilt);
    }

    private static Map<String, Map<String, List<String>>> immutableRoutes(
            Map<String, Map<String, List<String>>> routes) {
        Map<String, Map<String, List<String>>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> entry : routes.entrySet()) {
            Map<String, List<String>> byPort = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> port : entry.getValue().entrySet()) {
                byPort.put(port.getKey(), List.copyOf(port.getValue()));
            }
            result.put(entry.getKey(), Map.copyOf(byPort));
        }
        return Map.copyOf(result);
    }
}
