package nexa.framework.runtime.domain.deployment.model;

import nexa.framework.runtime.api.NexaCompiledNode;
import nexa.framework.runtime.api.NexaExecutionContext;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.workspace.model.InputExecutionPolicyDefinition;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledFlowTest {

    @Test
    void nodeEnableToggleMustPreserveAotExecutable() {
        NexaCompiledNode executable = new NexaCompiledNode() {
            @Override
            public void execute(RuntimeMessage msg, NexaExecutionContext context) {
                context.send(msg);
            }
        };

        CompiledNode node = node("script", NodeCategory.EXECUTOR, executable);

        CompiledFlow flow = flow(
                Map.of("script", node),
                Map.of(),
                Map.of("script", Map.of("default", List.of())));

        flow.setNodeEnabled("script", false);

        CompiledNode updated = flow.node("script");
        assertTrue(!updated.enabled());
        assertSame(executable, updated.executableNode());
    }

    @Test
    void targetsMustSupportFanOutFromOneNodePort() {
        CompiledFlow flow = flow(
                nodes("script", "debug-a", "debug-b"),
                connections(
                        connection("c1", "script", "default", "debug-a"),
                        connection("c2", "script", "default", "debug-b")),
                Map.of());

        assertEquals(List.of("debug-a", "debug-b"), flow.targets("script", "default"));
    }

    @Test
    void targetsMustSupportFanInIntoOneNode() {
        CompiledFlow flow = flow(
                nodes("input-a", "input-b", "script"),
                connections(
                        connection("c1", "input-a", "default", "script"),
                        connection("c2", "input-b", "default", "script")),
                Map.of());

        assertEquals(List.of("script"), flow.targets("input-a", "default"));
        assertEquals(List.of("script"), flow.targets("input-b", "default"));
    }

    @Test
    void disablingConnectionMustRemoveOnlyThatRoute() {
        CompiledConnection first = connection("c1", "script", "default", "debug-a");
        CompiledConnection second = connection("c2", "script", "default", "debug-b");

        CompiledFlow flow = flow(
                nodes("script", "debug-a", "debug-b"),
                Map.of("c1", first, "c2", second),
                Map.of());

        assertEquals(List.of("debug-a", "debug-b"), flow.targets("script", "default"));

        flow.setConnectionEnabled("c1", false);

        assertEquals(List.of("debug-b"), flow.targets("script", "default"));
        assertTrue(!flow.connectionEnabled("script", "default", "debug-a"));
        assertTrue(flow.connectionEnabled("script", "default", "debug-b"));
    }

    private static CompiledFlow flow(
            Map<String, CompiledNode> nodes,
            Map<String, CompiledConnection> connections,
            Map<String, Map<String, List<String>>> routes) {
        return new CompiledFlow(
                "main",
                "main",
                true,
                nodes,
                routes,
                connections);
    }

    private static Map<String, CompiledNode> nodes(String... ids) {
        Map<String, CompiledNode> nodes = new java.util.LinkedHashMap<>();
        for (String id : ids) {
            nodes.put(id, node(id, NodeCategory.EXECUTOR, null));
        }
        return nodes;
    }

    private static CompiledNode node(
            String id,
            NodeCategory category,
            NexaCompiledNode executable) {
        return new CompiledNode(
                id,
                category,
                "test-node",
                true,
                new InputExecutionPolicyDefinition(1),
                Map.of(),
                "nexa",
                executable);
    }

    private static Map<String, CompiledConnection> connections(CompiledConnection... connections) {
        Map<String, CompiledConnection> result = new java.util.LinkedHashMap<>();
        for (CompiledConnection connection : connections) {
            result.put(connection.id(), connection);
        }
        return result;
    }

    private static CompiledConnection connection(
            String id,
            String source,
            String port,
            String target) {
        return new CompiledConnection(id, source, port, target, true);
    }
}
