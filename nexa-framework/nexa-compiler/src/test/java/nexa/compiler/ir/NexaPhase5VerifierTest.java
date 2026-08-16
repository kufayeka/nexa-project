package nexa.compiler.ir;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import nexa.compiler.lang.NexaType;
import nexa.compiler.lang.SourceSpan;

/** Direct malformed-IR matrix for the Phase 5 verifier boundary. */
class NexaPhase5VerifierTest {
    private static final SourceSpan SPAN = new SourceSpan(0, 1);

    private List<NexaIrVerifier.Diagnostic> verify(NexaIr.Block... blocks) {
        NexaIr.Program program = new NexaIr.Program(
                "test",
                List.of(new NexaIr.Function("main", List.of(), List.of(blocks), NexaType.VOID, List.of())),
                Map.of(),
                1);
        return new NexaIrVerifier().verify(program);
    }

    @Test
    void rejectsUnknownControlFlowTargets() {
        var diagnostics = verify(new NexaIr.Block(0, List.of(), new NexaIr.Jump(99, SPAN)));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Unknown control-flow target block")),
                () -> diagnostics.toString());
    }

    @Test
    void rejectsDuplicateBlocksAndSsaDefinitions() {
        NexaIr.Value value = new NexaIr.Value(0, NexaType.INT32);
        var diagnostics = verify(
                new NexaIr.Block(0, List.of(new NexaIr.Const(value, 1, SPAN)), new NexaIr.Stop(SPAN)),
                new NexaIr.Block(0, List.of(new NexaIr.Const(value, 2, SPAN)), new NexaIr.Stop(SPAN)));

        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Duplicate block id")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("SSA value defined more than once")));
    }

    @Test
    void rejectsMalformedHostSignatures() {
        NexaIr.Value arg = new NexaIr.Value(0, NexaType.STRING);
        NexaIr.Value result = new NexaIr.Value(1, NexaType.OBJECT);
        NexaIr.HostCall call = new NexaIr.HostCall(
                result,
                new NexaIr.HostCapability("mqtt", "publish", "1"),
                new NexaIr.HostSignature(List.of(), NexaType.OBJECT),
                List.of(arg),
                SPAN);

        var diagnostics = verify(
                new NexaIr.Block(0,
                        List.of(new NexaIr.Const(arg, "topic", SPAN), call),
                        new NexaIr.Stop(SPAN)));

        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Host signature arity mismatch")),
                () -> diagnostics.toString());
    }

    @Test
    void rejectsHostArgumentAndResultTypeMismatches() {
        NexaIr.Value arg = new NexaIr.Value(0, NexaType.INT32);
        NexaIr.Value result = new NexaIr.Value(1, NexaType.STRING);
        NexaIr.HostCall call = new NexaIr.HostCall(
                result,
                new NexaIr.HostCapability("mqtt", "publish", "1"),
                new NexaIr.HostSignature(List.of(NexaType.STRING), NexaType.OBJECT),
                List.of(arg),
                SPAN);

        var diagnostics = verify(
                new NexaIr.Block(0,
                        List.of(new NexaIr.Const(arg, 1, SPAN), call),
                        new NexaIr.Stop(SPAN)));

        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Host argument type mismatch")),
                () -> diagnostics.toString());
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Host result type does not match")),
                () -> diagnostics.toString());
    }

    @Test
    void rejectsInvalidArrayAndObjectShapes() {
        NexaIr.Value wrongElement = new NexaIr.Value(0, NexaType.BOOLEAN);
        NexaIr.Value arrayResult = new NexaIr.Value(1, new NexaType.Array(NexaType.INT32));
        NexaIr.ArrayCreate array = new NexaIr.ArrayCreate(
                arrayResult, List.of(wrongElement), NexaType.INT32, SPAN);

        NexaIr.Value objectValue = new NexaIr.Value(2, NexaType.STRING);
        NexaIr.Value objectResult = new NexaIr.Value(3,
                new NexaType.ObjectType(Map.of("speed", NexaType.INT32)));
        NexaIr.ObjectCreate object = new NexaIr.ObjectCreate(
                objectResult,
                Map.of("speed", objectValue),
                new NexaType.ObjectType(Map.of("speed", NexaType.INT32)),
                SPAN);

        var diagnostics = verify(
                new NexaIr.Block(0,
                        List.of(
                                new NexaIr.Const(wrongElement, true, SPAN),
                                array,
                                new NexaIr.Const(objectValue, "bad", SPAN),
                                object),
                        new NexaIr.Stop(SPAN)));

        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Array element type mismatch")),
                () -> diagnostics.toString());
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Object field type mismatch")),
                () -> diagnostics.toString());
    }

    @Test
    void rejectsInvalidUnaryAndBinaryOperations() {
        NexaIr.Value bool = new NexaIr.Value(0, NexaType.BOOLEAN);
        NexaIr.Value one = new NexaIr.Value(1, NexaType.INT32);
        NexaIr.Value badUnary = new NexaIr.Value(2, NexaType.INT32);
        NexaIr.Value badBinary = new NexaIr.Value(3, NexaType.INT32);

        var diagnostics = verify(
                new NexaIr.Block(0,
                        List.of(
                                new NexaIr.Const(bool, true, SPAN),
                                new NexaIr.Const(one, 1, SPAN),
                                new NexaIr.Unary(badUnary, "-", bool, SPAN),
                                new NexaIr.Binary(badBinary, "&&", one, bool, SPAN)),
                        new NexaIr.Stop(SPAN)));

        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Unary numeric operator requires numeric operand")),
                () -> diagnostics.toString());
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Logical operator requires BOOLEAN operands")),
                () -> diagnostics.toString());
    }

    @Test
    void rejectsInvalidIteratorContracts() {
        NexaIr.Value number = new NexaIr.Value(0, NexaType.INT32);
        NexaIr.Value iterator = new NexaIr.Value(1, NexaType.INT32);
        NexaIr.Value hasNext = new NexaIr.Value(2, NexaType.INT32);
        NexaIr.Value next = new NexaIr.Value(3, NexaType.STRING);

        var diagnostics = verify(
                new NexaIr.Block(0,
                        List.of(
                                new NexaIr.Const(number, 1, SPAN),
                                new NexaIr.Iterate(iterator, number, SPAN),
                                new NexaIr.IterHasNext(hasNext, iterator, SPAN),
                                new NexaIr.IterNext(next, iterator, NexaType.INT32, SPAN)),
                        new NexaIr.Stop(SPAN)));

        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Iterate requires ARRAY or OBJECT")),
                () -> diagnostics.toString());
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Iterator handle must be OBJECT")),
                () -> diagnostics.toString());
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("IterHasNext result must be BOOLEAN")),
                () -> diagnostics.toString());
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("IterNext result type mismatch")),
                () -> diagnostics.toString());
    }

    @Test
    void rejectsInvalidArrayIndexTypeAndBranchTargets() {
        NexaIr.Value array = new NexaIr.Value(0, new NexaType.Array(NexaType.INT32));
        NexaIr.Value key = new NexaIr.Value(1, NexaType.BOOLEAN);
        NexaIr.Value result = new NexaIr.Value(2, NexaType.INT32);
        NexaIr.Value condition = new NexaIr.Value(3, NexaType.INT32);

        var diagnostics = verify(
                new NexaIr.Block(0,
                        List.of(
                                new NexaIr.Const(array, List.of(1), SPAN),
                                new NexaIr.Const(key, true, SPAN),
                                new NexaIr.LoadIndex(result, array, key, SPAN),
                                new NexaIr.Const(condition, 1, SPAN)),
                        new NexaIr.Branch(condition, 1, 99, SPAN)),
                new NexaIr.Block(1, List.of(), new NexaIr.Stop(SPAN)));

        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Array index must be numeric")),
                () -> diagnostics.toString());
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Branch condition must be BOOLEAN")),
                () -> diagnostics.toString());
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Unknown false branch block")),
                () -> diagnostics.toString());
    }
}
