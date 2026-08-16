package nexa.compiler.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompilerContractTest {

    @Test
    void compilationRequestIsImmutable() {
        var request = new CompilationRequest("workspace", "return 1;", CompilationTarget.JVM,
                Map.of("motor.speed", "FLOAT64"));

        assertEquals("workspace", request.workspaceId());
        assertEquals(CompilationTarget.JVM, request.target());
        assertEquals("FLOAT64", request.symbols().get("motor.speed"));
        assertThrows(UnsupportedOperationException.class,
                () -> request.symbols().put("x", "INT32"));
    }

    @Test
    void successfulResultRequiresArtifact() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompilationResult(true, java.util.List.of(), null));
    }

    @Test
    void artifactDefensivelyCopiesClassBytes() {
        byte[] bytes = {1, 2, 3};
        var artifact = new CompiledArtifact("workspace", CompilationTarget.JVM, "0.1.0",
                Map.of("example.Script", bytes));

        bytes[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, artifact.classBytes().get("example.Script"));
        assertThrows(UnsupportedOperationException.class,
                () -> artifact.classBytes().put("other", new byte[]{4}));
    }
}
