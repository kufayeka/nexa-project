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
    private final ConcurrentMap<String, CompiledNode> nodeById;
    private final ConcurrentMap<String, Map<String, List<String>>> routesByNodeAndPort;
    private final ConcurrentMap<String, CompiledConnection> connectionById;

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
        this.routesByNodeAndPort = new ConcurrentHashMap<>();
        this.connectionById = new ConcurrentHashMap<>(new LinkedHashMap<>(connectionById));

        rebuildRoutes();
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
        Map<String, Map<String, List<String>>> snapshot = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, List<String>>> entry : routesByNodeAndPort.entrySet()) {
            Map<String, List<String>> byPort = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> portEntry : entry.getValue().entrySet()) {
                byPort.put(portEntry.getKey(), List.copyOf(portEntry.getValue()));
            }
            snapshot.put(entry.getKey(), Collections.unmodifiableMap(byPort));
        }

        return Collections.unmodifiableMap(snapshot);
    }

    public Map<String, CompiledConnection> connectionById() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(connectionById));
    }

    public CompiledConnection connection(String connectionId) {
        return connectionById.get(connectionId);
    }

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
            throw new ValidationException(
                    "Unknown node id " + nodeId + " in flow " + flowId);
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

    public synchronized void setConnectionEnabled(String connectionId, boolean enabled) {
        CompiledConnection connection = connectionById.get(connectionId);

        if (connection == null) {
            throw new ValidationException(
                    "Unknown connection id " + connectionId + " in flow " + flowId);
        }

        if (connection.enabled() == enabled) {
            return;
        }

        connectionById.put(connectionId, new CompiledConnection(
                connection.id(),
                connection.sourceNodeId(),
                connection.sourcePort(),
                connection.targetNodeId(),
                enabled));

        rebuildRoutes();
    }

    private synchronized void rebuildRoutes() {
        Map<String, Map<String, List<String>>> rebuilt = new LinkedHashMap<>();

        for (String nodeId : nodeById.keySet()) {
            rebuilt.put(nodeId, new LinkedHashMap<>());
        }

        for (CompiledConnection connection : connectionById.values()) {
            if (!connection.enabled()) {
                continue;
            }

            Map<String, List<String>> byPort = rebuilt.get(connection.sourceNodeId());
            if (byPort == null) {
                throw new ValidationException(
                        "Connection " + connection.id()
                                + " references unknown source node "
                                + connection.sourceNodeId());
            }

            byPort.computeIfAbsent(connection.sourcePort(), ignored -> new ArrayList<>())
                    .add(connection.targetNodeId());
        }

        routesByNodeAndPort.clear();
        for (Map.Entry<String, Map<String, List<String>>> entry : rebuilt.entrySet()) {
            Map<String, List<String>> byPort = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> portEntry : entry.getValue().entrySet()) {
                byPort.put(portEntry.getKey(), List.copyOf(portEntry.getValue()));
            }
            routesByNodeAndPort.put(entry.getKey(), Collections.unmodifiableMap(byPort));
        }
    }
}
