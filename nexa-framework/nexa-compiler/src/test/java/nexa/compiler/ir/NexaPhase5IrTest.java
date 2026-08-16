package nexa.compiler.ir;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import nexa.compiler.lang.NexaType;

/** End-to-end regression coverage for the Phase 5 AST -> IR pipeline. */
class NexaPhase5IrTest {

    private NexaIr.Program compile(String source) {
        NexaIrCompiler.Result result = new NexaIrCompiler().compile(source);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertNotNull(result.ir());
        assertTrue(new NexaIrVerifier().verify(result.ir()).isEmpty(),
                () -> new NexaIrVerifier().verify(result.ir()).toString());
        return result.ir();
    }

    private List<NexaIr.Instruction> instructions(NexaIr.Program program) {
        return program.functions().stream()
                .flatMap(f -> f.blocks().stream())
                .flatMap(b -> b.instructions().stream())
                .toList();
    }

    @Test
    void lowersAllCoreExpressionFamilies() {
        NexaIr.Program program = compile("""
                let x: INT32 = 10 + 2;
                let y: INT32 = -x;
                let flag: BOOLEAN = x > 1 && x < 100;
                let xs: ARRAY<INT32> = [1, 2, 3];
                let n: INT32 = xs[1];
                let obj: OBJECT = { value: n };
                let v: OBJECT = obj.value;
                """);

        List<NexaIr.Instruction> all = instructions(program);
        assertTrue(all.stream().anyMatch(NexaIr.Const.class::isInstance));
        assertTrue(all.stream().anyMatch(NexaIr.Unary.class::isInstance));
        assertTrue(all.stream().anyMatch(NexaIr.Binary.class::isInstance));
        assertTrue(all.stream().anyMatch(NexaIr.ArrayCreate.class::isInstance));
        assertTrue(all.stream().anyMatch(NexaIr.LoadIndex.class::isInstance));
        assertTrue(all.stream().anyMatch(NexaIr.ObjectCreate.class::isInstance));
        assertTrue(all.stream().anyMatch(NexaIr.LoadField.class::isInstance));
        assertTrue(all.stream().anyMatch(NexaIr.LoadLocal.class::isInstance));
        assertTrue(all.stream().anyMatch(NexaIr.StoreLocal.class::isInstance));
    }

    @Test
    void lowersAllCoreStoreTargets() {
        NexaIr.Program program = compile("""
                let obj: OBJECT = { value: 1 };
                let xs: ARRAY<INT32> = [1, 2];
                obj.value = 2;
                xs[0] = 9;
                """);

        List<NexaIr.Instruction> all = instructions(program);
        assertTrue(all.stream().anyMatch(NexaIr.StoreField.class::isInstance));
        assertTrue(all.stream().anyMatch(NexaIr.StoreIndex.class::isInstance));
    }

    @Test
    void lowersNestedHostCapabilitiesWithoutPluginKnowledge() {
        NexaIr.Program program = compile("""
                mqtt.publish("topic", 1);
                mqtt.client.publish("topic", 2);
                system.io.write("hello");
                """);

        List<NexaIr.HostCall> calls = instructions(program).stream()
                .filter(NexaIr.HostCall.class::isInstance)
                .map(NexaIr.HostCall.class::cast)
                .toList();

        assertEquals(3, calls.size());
        assertEquals("mqtt.publish", calls.get(0).capability().qualifiedName());
        assertEquals("mqtt.client.publish", calls.get(1).capability().qualifiedName());
        assertEquals("system.io.write", calls.get(2).capability().qualifiedName());
        assertTrue(calls.stream().allMatch(c -> c.capability().version().equals("1")));
    }

    @Test
    void preservesNestedDirectCallsAsCalls() {
        NexaIr.Program program = compile("foo(bar(baz(1)));" );

        List<NexaIr.Call> calls = instructions(program).stream()
                .filter(NexaIr.Call.class::isInstance)
                .map(NexaIr.Call.class::cast)
                .toList();

        assertEquals(3, calls.size());
        assertEquals("baz", calls.get(0).target());
        assertEquals("bar", calls.get(1).target());
        assertEquals("foo", calls.get(2).target());
    }

    @Test
    void lowersLoopIntoIteratorAndExplicitControlFlow() {
        NexaIr.Program program = compile("""
                let xs: ARRAY<INT32> = [1, 2, 3];
                for (item: INT32 in xs) {
                    let doubled: INT32 = item * 2;
                }
                """);

        List<NexaIr.Instruction> all = instructions(program);
        assertTrue(all.stream().anyMatch(NexaIr.Iterate.class::isInstance));
        assertTrue(all.stream().anyMatch(NexaIr.IterHasNext.class::isInstance));
        assertTrue(all.stream().anyMatch(NexaIr.IterNext.class::isInstance));
        assertTrue(program.functions().get(0).blocks().stream()
                .anyMatch(b -> b.terminator() instanceof NexaIr.Branch));
        assertTrue(program.functions().get(0).blocks().stream()
                .anyMatch(b -> b.terminator() instanceof NexaIr.Jump));
    }

    @Test
    void lowersReturnAndStopsTheCurrentBlock() {
        NexaIr.Program program = compile("return 42;");
        List<NexaIr.Instruction> all = instructions(program);
        assertTrue(all.stream().anyMatch(NexaIr.Return.class::isInstance));
        assertTrue(program.functions().get(0).blocks().stream()
                .anyMatch(b -> b.terminator() instanceof NexaIr.Stop));
    }

    @Test
    void optimizerFoldsConstantsButNeverErasesHostBoundary() {
        NexaIr.Program arithmetic = compile("let x: INT32 = 2 + 3 * 4;");
        List<NexaIr.Instruction> folded = instructions(arithmetic);
        assertTrue(folded.stream().anyMatch(i -> i instanceof NexaIr.Const c
                && c.value() instanceof Number
                && ((Number) c.value()).doubleValue() == 14.0));

        NexaIr.Program host = compile("mqtt.publish(\"topic\", 2 + 3);");
        assertEquals(1, instructions(host).stream()
                .filter(NexaIr.HostCall.class::isInstance)
                .count());
    }

    @Test
    void irIsStableAndPrintable() {
        String source = "let xs: ARRAY<INT32> = [1, 2]; for (x: INT32 in xs) { let y: INT32 = x + 1; }";
        NexaIr.Program first = compile(source);
        NexaIr.Program second = compile(source);

        String a = new NexaIrPrinter().print(first);
        String b = new NexaIrPrinter().print(second);
        assertEquals(a, b);
        assertTrue(a.contains("nexa-ir v1"));
        assertTrue(a.contains("iterate"));
        assertTrue(a.contains("branch"));
    }

    @Test
    void irProgramCarriesUserTypesButNotPluginClasses() {
        NexaIr.Program program = compile("""
                type Motor = { speed: FLOAT64 };
                let motor: Motor = { speed: 10.0 };
                mqtt.publish("motor", motor.speed);
                """);

        assertTrue(program.types().containsKey("Motor"));
        assertTrue(program.types().get("Motor") instanceof NexaType.ObjectType);
        String printed = new NexaIrPrinter().print(program);
        assertFalse(printed.contains("mqtt.MqttPlugin"));
        assertTrue(printed.contains("mqtt.publish"));
    }
}
