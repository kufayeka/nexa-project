package nexa.framework.runtime.domain.execution;

import nexa.compiler.codegen.NexaBytecodeCompiler;
import nexa.compiler.ir.NexaIr;
import nexa.compiler.ir.NexaIrCompiler;
import nexa.framework.runtime.api.NexaCompiledNode;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end proof that Nexa source can cross the full compiler/runtime boundary:
 * source -> typed/verified IR -> ASM bytecode -> JVM class -> NexaCompiledNode
 * -> CompiledFlowRuntime -> output.
 */
class NexaFlowE2ETest {

    private static final InputExecutionPolicyDefinition DEFAULT_POLICY =
            new InputExecutionPolicyDefinition(Integer.MAX_VALUE);

    @Test
    void nexaSourceShouldCompileLoadAndExecuteInsideFlowRuntime() throws Exception {
        String source = """
                msg.payload = 21 + 21;
                send(msg);
                """;

        NexaIrCompiler.Result result = new NexaIrCompiler().compile(source);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertNotNull(result.ir());

        NexaIr.Program ir = result.ir();
        assertTrue(new nexa.compiler.ir.NexaIrVerifier().verify(ir).isEmpty(),
                () -> new nexa.compiler.ir.NexaIrVerifier().verify(ir).toString());

        byte[] bytecode = new NexaBytecodeCompiler().compile(ir, "nexa.generated.E2EFlowScript");
        assertArrayEquals(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE},
                java.util.Arrays.copyOf(bytecode, 4));

        NexaCompiledNode executable = load(bytecode);

        CompiledFlow flow = flow(
                nodes(
                        node("input", NodeCategory.INPUT, "manual", null),
                        node("script", NodeCategory.EXECUTOR, "nexa-script", executable),
                        node("debug", NodeCategory.OUTPUT, "debug", null)
                ),
                connections(
                        connection("c1", "input", "default", "script"),
                        connection("c2", "script", "default", "debug")
                )
        );

        CompiledFlowRuntime runtime = new CompiledFlowRuntime(flow);
        List<RuntimeMessage> received = new ArrayList<>();
        runtime.registerOutput("debug", received::add);

        runtime.trigger("input", new RuntimeMessage(Map.of("payload", 0)));

        assertEquals(1, received.size());
        assertEquals(42, received.getFirst().readRawValue("payload"));
    }

    @Test
    void compiledNexaSourceShouldFanOutThroughRuntime() throws Exception {
        NexaCompiledNode executable = compile("send(msg);");

        CompiledFlow flow = flow(
                nodes(
                        node("input", NodeCategory.INPUT, "manual", null),
                        node("script", NodeCategory.EXECUTOR, "nexa-script", executable),
                        node("debug-a", NodeCategory.OUTPUT, "debug", null),
                        node("debug-b", NodeCategory.OUTPUT, "debug", null)
                ),
                connections(
                        connection("c1", "input", "default", "script"),
                        connection("c2", "script", "default", "debug-a"),
                        connection("c3", "script", "default", "debug-b")
                )
        );

        CompiledFlowRuntime runtime = new CompiledFlowRuntime(flow);
        List<Object> received = new ArrayList<>();
        runtime.registerOutput("debug-a", msg -> received.add(msg.readRawValue("payload")));
        runtime.registerOutput("debug-b", msg -> received.add(msg.readRawValue("payload")));

        runtime.trigger("input", new RuntimeMessage(Map.of("payload", 7)));

        assertEquals(List.of(7, 7), received);
    }

    private static NexaCompiledNode compile(String source) throws Exception {
        NexaIrCompiler.Result result = new NexaIrCompiler().compile(source);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertNotNull(result.ir());

        byte[] bytecode = new NexaBytecodeCompiler().compile(result.ir());
        return load(bytecode);
    }

    private static NexaCompiledNode load(byte[] bytes) throws Exception {
        class ByteLoader extends ClassLoader {
            ByteLoader() {
                super(NexaFlowE2ETest.class.getClassLoader());
            }

            Class<?> define(byte[] value) {
                return defineClass(null, value, 0, value.length);
            }
        }

        return (NexaCompiledNode) new ByteLoader()
                .define(bytes)
                .getDeclaredConstructor()
                .newInstance();
    }

    private static CompiledNode node(
            String id,
            NodeCategory category,
            String type,
            NexaCompiledNode executable) {
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
            String id,
            String source,
            String port,
            String target) {
        return new CompiledConnection(id, source, port, target, true);
    }

    private static Map<String, CompiledNode> nodes(CompiledNode... nodes) {
        Map<String, CompiledNode> result = new LinkedHashMap<>();
        for (CompiledNode node : nodes) {
            result.put(node.id(), node);
        }
        return result;
    }

    private static Map<String, CompiledConnection> connections(CompiledConnection... connections) {
        Map<String, CompiledConnection> result = new LinkedHashMap<>();
        for (CompiledConnection connection : connections) {
            result.put(connection.id(), connection);
        }
        return result;
    }

    private static CompiledFlow flow(
            Map<String, CompiledNode> nodes,
            Map<String, CompiledConnection> connections) {
        Map<String, Map<String, List<String>>> routes = new LinkedHashMap<>();
        for (CompiledConnection connection : connections.values()) {
            routes.computeIfAbsent(connection.sourceNodeId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(connection.sourcePort(), ignored -> new ArrayList<>())
                    .add(connection.targetNodeId());
        }
        return new CompiledFlow("e2e-flow", "test", true, nodes, routes, connections);
    }
}
