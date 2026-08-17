package nexa.framework.runtime.domain.execution;

import nexa.framework.runtime.api.NexaCompiledNode;
import nexa.framework.runtime.api.NexaExecutionContext;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.deployment.model.CompiledConnection;
import nexa.framework.runtime.domain.deployment.model.CompiledFlow;
import nexa.framework.runtime.domain.deployment.model.CompiledNode;
import nexa.framework.runtime.domain.workspace.model.InputExecutionPolicyDefinition;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompiledFlowRuntimeTest {

    private static final InputExecutionPolicyDefinition DEFAULT_POLICY =
            new InputExecutionPolicyDefinition(Integer.MAX_VALUE);

    @Test
    void triggerShouldExecuteCompiledNodeAndDeliverMessageToOutput() {
        NexaCompiledNode script = (msg, context) -> {
            int payload = (Integer) msg.readRawValue("payload");
            msg.writeValue("payload", payload * 2);
            context.send(msg);
        };

        CompiledFlow flow = flow(
                nodes(
                        node("input", NodeCategory.INPUT, "manual", null),
                        node("script", NodeCategory.EXECUTOR, "nexa-script", script),
                        node("debug", NodeCategory.OUTPUT, "debug", null)
                ),
                connections(
                        connection("c1", "input", "default", "script"),
                        connection("c2", "script", "default", "debug")
                ),
                routes(
                        route("input", "default", List.of("script")),
                        route("script", "default", List.of("debug"))
                )
        );

        CompiledFlowRuntime runtime = new CompiledFlowRuntime(flow);
        List<RuntimeMessage> received = new ArrayList<>();
        runtime.registerOutput("debug", received::add);

        runtime.trigger("input", new RuntimeMessage(Map.of("payload", 10)));

        assertEquals(1, received.size());
        assertEquals(20, received.get(0).readRawValue("payload"));
    }

    @Test
    void oneExecutorOutputShouldFanOutToMultipleOutputs() {
        NexaCompiledNode script = (msg, context) -> context.send(msg);

        CompiledFlow flow = flow(
                nodes(
                        node("input", NodeCategory.INPUT, "manual", null),
                        node("script", NodeCategory.EXECUTOR, "nexa-script", script),
                        node("debug-a", NodeCategory.OUTPUT, "debug", null),
                        node("debug-b", NodeCategory.OUTPUT, "debug", null)
                ),
                connections(
                        connection("c1", "input", "default", "script"),
                        connection("c2", "script", "default", "debug-a"),
                        connection("c3", "script", "default", "debug-b")
                ),
                routes(
                        route("input", "default", List.of("script")),
                        route("script", "default", List.of("debug-a", "debug-b"))
                )
        );

        CompiledFlowRuntime runtime = new CompiledFlowRuntime(flow);
        List<Object> received = new ArrayList<>();
        runtime.registerOutput("debug-a", msg -> received.add(msg.readRawValue("payload")));
        runtime.registerOutput("debug-b", msg -> received.add(msg.readRawValue("payload")));

        runtime.trigger("input", new RuntimeMessage(Map.of("payload", 42)));

        assertEquals(List.of(42, 42), received);
    }

    @Test
    void multipleInputsShouldFanInIntoOneExecutor() {
        NexaCompiledNode script = (msg, context) -> {
            msg.writeValue("seen", true);
            context.send(msg);
        };

        CompiledFlow flow = flow(
                nodes(
                        node("manual-a", NodeCategory.INPUT, "manual", null),
                        node("manual-b", NodeCategory.INPUT, "manual", null),
                        node("script", NodeCategory.EXECUTOR, "nexa-script", script),
                        node("debug", NodeCategory.OUTPUT, "debug", null)
                ),
                connections(
                        connection("c1", "manual-a", "default", "script"),
                        connection("c2", "manual-b", "default", "script"),
                        connection("c3", "script", "default", "debug")
                ),
                routes(
                        route("manual-a", "default", List.of("script")),
                        route("manual-b", "default", List.of("script")),
                        route("script", "default", List.of("debug"))
                )
        );

        CompiledFlowRuntime runtime = new CompiledFlowRuntime(flow);
        List<String> received = new ArrayList<>();
        runtime.registerOutput("debug", msg -> received.add((String) msg.readRawValue("source")));

        runtime.trigger("manual-a", new RuntimeMessage(Map.of("source", "a")));
        runtime.trigger("manual-b", new RuntimeMessage(Map.of("source", "b")));

        assertEquals(List.of("a", "b"), received);
    }

    private static CompiledNode node(String id, NodeCategory category, String type, NexaCompiledNode executable) {
        return new CompiledNode(
                id,
                category,
                type,
                true,
                DEFAULT_POLICY,
                Map.of(),
                category == NodeCategory.EXECUTOR ? "nexa" : null,
                executable);
    }

    private static CompiledConnection connection(
            String id, String source, String port, String target) {
        return new CompiledConnection(id, source, port, target, true);
    }

    private static Map<String, CompiledNode> nodes(CompiledNode... nodes) {
        Map<String, CompiledNode> result = new LinkedHashMap<>();
        for (CompiledNode node : nodes) result.put(node.id(), node);
        return result;
    }

    private static Map<String, CompiledConnection> connections(CompiledConnection... connections) {
        Map<String, CompiledConnection> result = new LinkedHashMap<>();
        for (CompiledConnection connection : connections) result.put(connection.id(), connection);
        return result;
    }

    private static Map<String, Map<String, List<String>>> routes(Object... values) {
        Map<String, Map<String, List<String>>> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put((String) values[i], Map.of((String) values[i + 1], List.of()));
        }
        return result;
    }

    private static Object[] route(String nodeId, String port, List<String> targets) {
        return new Object[]{nodeId, port, targets};
    }

    private static CompiledFlow flow(
            Map<String, CompiledNode> nodes,
            Map<String, CompiledConnection> connections,
            Map<String, Map<String, List<String>>> ignoredRoutes) {
        Map<String, Map<String, List<String>>> routes = new LinkedHashMap<>();
        for (CompiledConnection connection : connections.values()) {
            routes.computeIfAbsent(connection.sourceNodeId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(connection.sourcePort(), ignored -> new ArrayList<>())
                    .add(connection.targetNodeId());
        }
        return new CompiledFlow("flow", "test", true, nodes, routes, connections);
    }
}
