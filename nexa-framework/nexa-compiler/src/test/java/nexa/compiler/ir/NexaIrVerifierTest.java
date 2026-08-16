package nexa.compiler.ir;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import nexa.compiler.lang.NexaType;
import nexa.compiler.lang.SourceSpan;

class NexaIrVerifierTest {

    private static final SourceSpan SPAN = new SourceSpan(0, 1);

    @Test
    void acceptsValidTypedHostCall() {
        NexaIr.Value arg = new NexaIr.Value(0, NexaType.STRING);
        NexaIr.Value result = new NexaIr.Value(1, NexaType.OBJECT);
        NexaIr.HostCapability capability = new NexaIr.HostCapability("mqtt", "publish", "1");
        NexaIr.HostSignature signature = new NexaIr.HostSignature(List.of(NexaType.STRING), NexaType.OBJECT);

        NexaIr.Block block = new NexaIr.Block(
                0,
                List.of(
                        new NexaIr.Const(arg, "topic", SPAN),
                        new NexaIr.HostCall(result, capability, signature, List.of(arg), SPAN)),
                new NexaIr.Stop(SPAN));

        NexaIr.Program program = new NexaIr.Program(
                "test",
                List.of(new NexaIr.Function("main", List.of(), List.of(block), NexaType.VOID, List.of())),
                Map.of(),
                1);

        assertTrue(new NexaIrVerifier().verify(program).isEmpty());
    }

    @Test
    void rejectsBranchWithNonBooleanCondition() {
        NexaIr.Value condition = new NexaIr.Value(0, NexaType.INT32);
        NexaIr.Block entry = new NexaIr.Block(
                0,
                List.of(new NexaIr.Const(condition, 1, SPAN)),
                new NexaIr.Branch(condition, 1, 2, SPAN));
        NexaIr.Block left = new NexaIr.Block(1, List.of(), new NexaIr.Stop(SPAN));
        NexaIr.Block right = new NexaIr.Block(2, List.of(), new NexaIr.Stop(SPAN));

        NexaIr.Program program = new NexaIr.Program(
                "test",
                List.of(new NexaIr.Function("main", List.of(), List.of(entry, left, right), NexaType.VOID, List.of())),
                Map.of(),
                1);

        assertTrue(new NexaIrVerifier().verify(program).stream()
                .anyMatch(d -> d.message().contains("Branch condition must be BOOLEAN")));
    }

    @Test
    void rejectsConstLocalWrittenTwice() {
        NexaIr.Local local = new NexaIr.Local("x", NexaType.INT32, true);
        NexaIr.Value one = new NexaIr.Value(0, NexaType.INT32);
        NexaIr.Value two = new NexaIr.Value(1, NexaType.INT32);
        NexaIr.Block block = new NexaIr.Block(
                0,
                List.of(
                        new NexaIr.Const(one, 1, SPAN),
                        new NexaIr.Const(two, 2, SPAN),
                        new NexaIr.StoreLocal(null, "x", one, true, SPAN),
                        new NexaIr.StoreLocal(null, "x", two, true, SPAN)),
                new NexaIr.Stop(SPAN));

        NexaIr.Program program = new NexaIr.Program(
                "test",
                List.of(new NexaIr.Function("main", List.of(local), List.of(block), NexaType.VOID, List.of())),
                Map.of(),
                1);

        assertTrue(new NexaIrVerifier().verify(program).stream()
                .anyMatch(d -> d.message().contains("Const local assigned more than once")));
    }
}
