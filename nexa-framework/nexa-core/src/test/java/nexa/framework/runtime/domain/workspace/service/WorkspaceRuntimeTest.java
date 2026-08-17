package nexa.framework.runtime.domain.workspace.service;

import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.deployment.service.FlowCompiler;
import nexa.framework.runtime.domain.deployment.service.FlowValidator;
import nexa.framework.runtime.domain.workspace.model.ConnectionDefinition;
import nexa.framework.runtime.domain.workspace.model.FlowDefinition;
import nexa.framework.runtime.domain.workspace.model.InputExecutionPolicyDefinition;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import nexa.framework.runtime.domain.workspace.model.NodeDefinition;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceRuntimeTest {
    private static final InputExecutionPolicyDefinition POLICY =
            new InputExecutionPolicyDefinition(Integer.MAX_VALUE);

    @Test
    void workspaceShouldCompileFlowExecuteNexaAndPersistSharedTagState() {
        String script = """
                let input: INT32 = 7;
                $speed = input;
                let doubled: INT32 = $speed * 2;
                msg.payload = doubled;
                send(msg);
                """;

        WorkspaceDefinition workspace = new WorkspaceDefinition(
                "workspace-level-5", true, List.of(),
                List.of(new FlowDefinition("flow-main", "Main", true,
                        List.of(
                                node("input", NodeCategory.INPUT, "manual", null),
                                node("script", NodeCategory.EXECUTOR, "script", script),
                                node("debug", NodeCategory.OUTPUT, "debug", null)),
                        List.of(
                                connection("c1", "input", "default", "script"),
                                connection("c2", "script", "default", "debug"))
                ))
        );

        WorkspaceRuntime runtime = new WorkspaceRuntime(new FlowCompiler(new FlowValidator()));
        runtime.deploy(workspace);

        assertTrue(runtime.tagSlots().containsKey("speed"));
        List<Integer> received = new ArrayList<>();
        runtime.registerOutput("flow-main", "debug",
                msg -> received.add((Integer) msg.readRawValue("payload")));

        runtime.trigger("flow-main", "input", new RuntimeMessage(Map.of()));

        assertEquals(List.of(14), received);
        assertEquals(7, runtime.readTagInt("speed"));
    }

    @Test
    void tagStateShouldBeSharedAcrossFlowsInOneWorkspace() {
        String writer = """
                let value: INT32 = 42;
                $shared = value;
                send(msg);
                """;
        String reader = """
                msg.payload = $shared;
                send(msg);
                """;

        WorkspaceDefinition workspace = new WorkspaceDefinition(
                "workspace-shared-tags", true, List.of(),
                List.of(
                        flow("writer", "input-a", "writer-script", writer),
                        flow("reader", "input-b", "reader-script", reader)
                )
        );

        WorkspaceRuntime runtime = new WorkspaceRuntime(new FlowCompiler(new FlowValidator()));
        runtime.deploy(workspace);
        List<Integer> received = new ArrayList<>();
        runtime.registerOutput("reader", "debug-reader",
                msg -> received.add((Integer) msg.readRawValue("payload")));

        runtime.trigger("writer", "input-a", new RuntimeMessage(Map.of()));
        runtime.trigger("reader", "input-b", new RuntimeMessage(Map.of()));

        assertEquals(List.of(42), received);
        assertEquals(42, runtime.readTagInt("shared"));
    }

    private static FlowDefinition flow(String id, String inputId, String scriptNodeId, String script) {
        return new FlowDefinition(id, id, true,
                List.of(
                        node(inputId, NodeCategory.INPUT, "manual", null),
                        node(scriptNodeId, NodeCategory.EXECUTOR, "script", script),
                        node("debug-" + id, NodeCategory.OUTPUT, "debug", null)),
                List.of(
                        connection("c1-" + id, inputId, "default", scriptNodeId),
                        connection("c2-" + id, scriptNodeId, "default", "debug-" + id))
        );
    }

    private static NodeDefinition node(String id, NodeCategory category, String type, String script) {
        Map<String, Object> config = script == null ? Map.of() : Map.of("script", script);
        return new NodeDefinition(id, category, type, "nexa", true, POLICY, config);
    }

    private static ConnectionDefinition connection(String id, String source, String port, String target) {
        return new ConnectionDefinition(id, source, port, target, true);
    }
}
