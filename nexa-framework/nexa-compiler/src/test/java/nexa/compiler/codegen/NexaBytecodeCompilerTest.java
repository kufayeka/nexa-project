package nexa.compiler.codegen;

import nexa.compiler.ir.NexaIr;
import nexa.compiler.ir.NexaIrCompiler;
import nexa.compiler.ir.NexaIrVerifier;
import nexa.framework.runtime.api.NexaCompiledNode;
import nexa.framework.runtime.api.NexaExecutionContext;
import nexa.framework.runtime.api.model.RuntimeMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NexaBytecodeCompilerTest {
    @Test
    void generatesLoadableCompiledNode() throws Exception {
        NexaIr.Program program = compile("mqtt.capture(2 + 3);");
        byte[] bytes = new NexaBytecodeCompiler().compile(program);

        assertTrue(bytes.length > 0);
        assertEquals((byte) 0xCA, bytes[0]);
        assertEquals((byte) 0xFE, bytes[1]);
        assertEquals((byte) 0xBA, bytes[2]);
        assertEquals((byte) 0xBE, bytes[3]);
        assertNotNull(load(bytes));
    }

    @Test
    void executesPrimitiveArithmeticAndHostBoundary() throws Exception {
        NexaIr.Program program = compile("mqtt.capture(40 + 2);");
        NexaCompiledNode node = load(new NexaBytecodeCompiler().compile(program));
        CapturingContext context = new CapturingContext();

        node.execute(new RuntimeMessage(), context);

        assertEquals("mqtt", context.namespace);
        assertEquals("capture", context.name);
        assertEquals(List.of(42), context.args);
    }

    @Test
    void executesObjectsArraysAndFieldIndexAccess() throws Exception {
        NexaIr.Program program = compile("""
                let obj: OBJECT = { value: 10 };
                let xs: ARRAY<INT32> = [1, 2, 3];
                mqtt.capture(obj.value, xs[1]);
                """);
        NexaCompiledNode node = load(new NexaBytecodeCompiler().compile(program));
        CapturingContext context = new CapturingContext();

        node.execute(new RuntimeMessage(), context);

        assertEquals(List.of(10, 2), context.args);
    }

    @Test
    void executesLoopAndBranches() throws Exception {
        NexaIr.Program program = compile("""
                let xs: ARRAY<INT32> = [1, 2, 3];
                let total: INT32 = 0;
                for (item: INT32 in xs) {
                    total = total + item;
                }
                mqtt.capture(total);
                """);
        NexaCompiledNode node = load(new NexaBytecodeCompiler().compile(program));
        CapturingContext context = new CapturingContext();

        node.execute(new RuntimeMessage(), context);

        assertEquals(List.of(6), context.args);
    }

    @Test
    void executesHostCallWithPrimitiveAndStringArguments() throws Exception {
        NexaIr.Program program = compile("mqtt.capture(\"sensor\", 12.5);");
        NexaCompiledNode node = load(new NexaBytecodeCompiler().compile(program));
        CapturingContext context = new CapturingContext();

        node.execute(new RuntimeMessage(), context);

        assertEquals(List.of("sensor", 12.5), context.args);
    }

    private NexaIr.Program compile(String source) {
        NexaIrCompiler.Result result = new NexaIrCompiler().compile(source);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertNotNull(result.ir());
        assertTrue(new NexaIrVerifier().verify(result.ir()).isEmpty(),
                () -> new NexaIrVerifier().verify(result.ir()).toString());
        return result.ir();
    }

    private NexaCompiledNode load(byte[] bytes) throws Exception {
        class ByteLoader extends ClassLoader {
            ByteLoader() { super(NexaBytecodeCompilerTest.class.getClassLoader()); }
            Class<?> define(byte[] value) { return defineClass(null, value, 0, value.length); }
        }
        return (NexaCompiledNode) new ByteLoader().define(bytes).getDeclaredConstructor().newInstance();
    }

    private static final class CapturingContext implements NexaExecutionContext {
        String namespace;
        String name;
        List<Object> args = List.of();
        private final Map<Integer, Object> tags = new HashMap<>();

        @Override public void send(RuntimeMessage msg) {}
        @Override public void send(String port, RuntimeMessage msg) {}
        @Override public void send(List<String> ports, RuntimeMessage msg) {}

        @Override
        public Object callHostCapability(String namespace, String name, List<Object> args) {
            this.namespace = namespace;
            this.name = name;
            this.args = new ArrayList<>(args);
            return null;
        }

        @Override public int readTagInt(int index) { return ((Number) tags.getOrDefault(index, 0)).intValue(); }
        @Override public void writeTagInt(int index, int value) { tags.put(index, value); }
        @Override public long readTagLong(int index) { return ((Number) tags.getOrDefault(index, 0L)).longValue(); }
        @Override public void writeTagLong(int index, long value) { tags.put(index, value); }
        @Override public double readTagDouble(int index) { return ((Number) tags.getOrDefault(index, 0.0)).doubleValue(); }
        @Override public void writeTagDouble(int index, double value) { tags.put(index, value); }
        @Override public Object readTagObject(int index) { return tags.get(index); }
        @Override public void writeTagObject(int index, Object value) { tags.put(index, value); }
    }
}