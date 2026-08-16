package nexa.compiler.ir;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

public class NexaIrCompilerTest {

    @Test
    void lowersPrimitiveOperationsToTypedIr() {
        NexaIrCompiler.Result result = new NexaIrCompiler().compile(""
                + "let x: INT32 = 1;"
                + "let y: INT32 = x + 2;"
                + "x = y * 3;");

        assertTrue(result.success(), () -> result.diagnostics().toString());

        NexaIr.Function main = result.ir().functions().get(0);
        List<NexaIr.Instruction> instructions = main.blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .toList();

        assertTrue(instructions.stream().anyMatch(i -> i instanceof NexaIr.Binary b && b.op().equals("+")));
        assertTrue(instructions.stream().anyMatch(i -> i instanceof NexaIr.Binary b && b.op().equals("*")));
        assertTrue(instructions.stream().anyMatch(i -> i instanceof NexaIr.StoreLocal));
    }

    @Test
    void lowersDottedCallsAsDynamicHostCapabilities() {
        NexaIrCompiler.Result result = new NexaIrCompiler().compile(
                "mqtt.publish(\"nexa/test\", 42);");

        assertTrue(result.success(), () -> result.diagnostics().toString());

        List<NexaIr.HostCall> calls = result.ir().functions().get(0).blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .filter(NexaIr.HostCall.class::isInstance)
                .map(NexaIr.HostCall.class::cast)
                .toList();

        assertEquals(1, calls.size());
        assertEquals("mqtt", calls.get(0).capability().namespace());
        assertEquals("publish", calls.get(0).capability().name());
        assertEquals(2, calls.get(0).args().size());
        assertEquals("mqtt.publish", calls.get(0).capability().qualifiedName());
    }

    @Test
    void lowersForLoopToExplicitControlFlow() {
        NexaIrCompiler.Result result = new NexaIrCompiler().compile(""
                + "let xs: ARRAY<INT32> = [1, 2, 3];"
                + "for (item: INT32 in xs) {"
                + "  let doubled: INT32 = item * 2;"
                + "}");

        assertTrue(result.success(), () -> result.diagnostics().toString());

        NexaIr.Function main = result.ir().functions().get(0);
        assertTrue(main.blocks().size() >= 4);
        assertTrue(main.blocks().stream().anyMatch(b -> b.terminator() instanceof NexaIr.Branch));
        assertTrue(main.blocks().stream().anyMatch(b -> b.terminator() instanceof NexaIr.Jump));
    }

    @Test
    void rejectsFrontendErrorsBeforeIrLowering() {
        NexaIrCompiler.Result result = new NexaIrCompiler().compile(
                "let x: INT32 = true;");

        assertFalse(result.success());
        assertNull(result.ir());
        assertFalse(result.diagnostics().isEmpty());
    }

    @Test
    void optimizesPureConstantArithmetic() {
        NexaIrCompiler.Result result = new NexaIrCompiler().compile(
                "let x: INT32 = 2 + 3 * 4;");

        assertTrue(result.success(), () -> result.diagnostics().toString());

        List<NexaIr.Instruction> instructions = result.ir().functions().get(0).blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .toList();

        assertTrue(instructions.stream().anyMatch(i -> i instanceof NexaIr.Const c
                && c.value() instanceof Number
                && ((Number) c.value()).doubleValue() == 14.0));
    }

    @Test
    void preservesHostCallsDuringOptimization() {
        NexaIrCompiler.Result result = new NexaIrCompiler().compile(
                "mqtt.publish(\"nexa/test\", 2 + 3);");

        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertEquals(1, result.ir().functions().get(0).blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .filter(NexaIr.HostCall.class::isInstance)
                .count());
    }

    @Test
    void printerIsDeterministicAndContainsControlFlow() {
        NexaIrCompiler.Result result = new NexaIrCompiler().compile(""
                + "let xs: ARRAY<INT32> = [1, 2];"
                + "for (item: INT32 in xs) { let x: INT32 = item + 1; }");

        assertTrue(result.success(), () -> result.diagnostics().toString());
        String printed = new NexaIrPrinter().print(result.ir());
        assertTrue(printed.contains("nexa-ir v1"));
        assertTrue(printed.contains("iterate"));
        assertTrue(printed.contains("branch"));
        assertTrue(printed.contains("jump block"));
    }
}
