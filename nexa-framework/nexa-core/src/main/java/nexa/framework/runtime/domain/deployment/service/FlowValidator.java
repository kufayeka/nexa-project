package nexa.framework.runtime.domain.deployment.service;

import nexa.framework.runtime.domain.deployment.exception.ValidationException;

import nexa.framework.runtime.domain.workspace.model.ConnectionDefinition;
import nexa.framework.runtime.domain.workspace.model.FlowDefinition;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import nexa.framework.runtime.domain.workspace.model.NodeDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FlowValidator {

    public void validate(FlowDefinition definition) {
        if (definition == null) {
            throw new ValidationException("Flow definition must not be null");
        }

        if (definition.id() == null || definition.id().isBlank()) {
            throw new ValidationException("Flow id must not be blank");
        }

        if (definition.nodes().isEmpty()) {
            throw new ValidationException("Flow " + definition.id() + " must contain nodes");
        }

        Map<String, NodeDefinition> nodeById = validateNodes(definition);
        validateConnections(definition, nodeById);
        validateTopology(definition, nodeById);
    }

    private Map<String, NodeDefinition> validateNodes(FlowDefinition definition) {
        Map<String, NodeDefinition> nodeById = new LinkedHashMap<>();
        int inputCount = 0;

        for (NodeDefinition node : definition.nodes()) {
            if (node == null) {
                throw new ValidationException("Flow " + definition.id() + " contains null node definition");
            }

            if (node.id() == null || node.id().isBlank()) {
                throw new ValidationException("Flow " + definition.id() + " has node with blank id");
            }

            if (node.category() == null) {
                throw new ValidationException(
                        "Node " + node.id() + " in flow " + definition.id() + " must declare category");
            }

            if (node.type() == null || node.type().isBlank()) {
                throw new ValidationException(
                        "Node " + node.id() + " in flow " + definition.id() + " must declare type");
            }

            NodeDefinition previous = nodeById.putIfAbsent(node.id(), node);
            if (previous != null) {
                throw new ValidationException("Flow " + definition.id() + " has duplicate node id " + node.id());
            }

            if (node.category() == NodeCategory.INPUT) {
                inputCount++;
            }
        }

        if (inputCount < 1) {
            throw new ValidationException("Flow " + definition.id() + " must define at least one input node");
        }

        return nodeById;
    }

    private void validateConnections(FlowDefinition definition, Map<String, NodeDefinition> nodeById) {
        for (ConnectionDefinition connection : definition.connections()) {
            if (connection == null) {
                throw new ValidationException("Flow " + definition.id() + " contains null connection");
            }

            NodeDefinition source = nodeById.get(connection.sourceNodeId());
            if (source == null) {
                throw new ValidationException(
                        "Flow " + definition.id() + " references unknown source node " + connection.sourceNodeId());
            }

            NodeDefinition target = nodeById.get(connection.targetNodeId());
            if (target == null) {
                throw new ValidationException(
                        "Flow " + definition.id() + " references unknown target node " + connection.targetNodeId());
            }

            if (source.category() == NodeCategory.OUTPUT) {
                throw new ValidationException("Output node " + source.id() + " in flow " + definition.id()
                        + " cannot have outgoing connection");
            }

            if (target.category() == NodeCategory.INPUT) {
                throw new ValidationException("Input node " + target.id() + " in flow " + definition.id()
                        + " cannot have incoming connection");
            }

            if (Objects.equals(source.id(), target.id())) {
                throw new ValidationException(
                        "Node " + source.id() + " in flow " + definition.id() + " cannot connect to itself");
            }
        }
    }

    private void validateTopology(FlowDefinition definition, Map<String, NodeDefinition> nodeById) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();

        for (String nodeId : nodeById.keySet()) {
            indegree.put(nodeId, 0);
            adjacency.put(nodeId, new ArrayList<>());
        }

        for (ConnectionDefinition connection : definition.connections()) {
            indegree.put(connection.targetNodeId(), indegree.get(connection.targetNodeId()) + 1);
            adjacency.get(connection.sourceNodeId()).add(connection.targetNodeId());
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        int visited = 0;
        while (!queue.isEmpty()) {
            String nodeId = queue.removeFirst();
            visited++;

            for (String next : adjacency.get(nodeId)) {
                int nextIndegree = indegree.get(next) - 1;
                indegree.put(next, nextIndegree);
                if (nextIndegree == 0) {
                    queue.add(next);
                }
            }
        }

        if (visited != nodeById.size()) {
            throw new ValidationException("Flow " + definition.id() + " contains cycle in topology");
        }

        Set<String> reachable = collectReachableNodes(definition, nodeById);
        if (reachable.size() != nodeById.size()) {
            throw new ValidationException("Flow " + definition.id() + " contains unreachable node topology");
        }
    }

    private Set<String> collectReachableNodes(FlowDefinition definition, Map<String, NodeDefinition> nodeById) {
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();

        for (NodeDefinition node : nodeById.values()) {
            if (node.category() == NodeCategory.INPUT) {
                queue.add(node.id());
                reachable.add(node.id());
            }
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        for (String nodeId : nodeById.keySet()) {
            adjacency.put(nodeId, new ArrayList<>());
        }

        for (ConnectionDefinition connection : definition.connections()) {
            adjacency.get(connection.sourceNodeId()).add(connection.targetNodeId());
        }

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();

            for (String next : adjacency.get(current)) {
                if (reachable.add(next)) {
                    queue.add(next);
                }
            }
        }

        return reachable;
    }
}


