package nexa.compiler.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

/** API-boundary regression tests for the compiler contract used by Phase 5. */
class CompilerPhase5ApiTest {

    @Test
    void requestCopiesSymbolsAndDefaultsNullSymbolsToEmpty() {
        Map<String, String> source = new HashMap<>();
        source.put("motor.speed", "FLOAT64");

        CompilationRequest request = new CompilationRequest(
                "ws",
                "let x: INT32 = 1;",
                CompilationTarget.JVM,
                source);

        source.put("injected", "OBJECT");
        assertEquals(Map.of("motor.speed", "FLOAT64"), request.symbols());
        assertEquals(Map.of(), new CompilationRequest("ws", "", CompilationTarget.JVM, null).symbols());
        assertThrows(UnsupportedOperationException.class,
                () -> request.symbols().put("x", "INT32"));
    }

    @Test
    void requestRejectsMissingRequiredFields() {
        assertThrows(NullPointerException.class,
                () -> new CompilationRequest(null, "", CompilationTarget.JVM, Map.of()));
        assertThrows(NullPointerException.class,
                () -> new CompilationRequest("ws", null, CompilationTarget.JVM, Map.of()));
        assertThrows(NullPointerException.class,
                () -> new CompilationRequest("ws", "", null, Map.of()));
    }

    @Test
    void failedCompilationMayHaveDiagnosticsAndNoArtifact() {
        var diagnostic = new CompilationDiagnostic(
                CompilationDiagnostic.Severity.ERROR,
                "E001",
                "bad source",
                1,
                1);
        var result = new CompilationResult(false, List.of(diagnostic), null);

        assertFalse(result.successful());
        assertEquals(1, result.diagnostics().size());
        assertNull(result.artifact());
        assertThrows(UnsupportedOperationException.class,
                () -> result.diagnostics().add(diagnostic));
    }

    @Test
    void successfulCompilationRequiresArtifactAndDiagnosticsAreImmutable() {
        var artifact = new CompiledArtifact(
                "ws",
                CompilationTarget.JVM,
                "1.0.0",
                Map.of("example.Script", new byte[]{1, 2, 3}));
        var result = new CompilationResult(true, List.of(), artifact);

        assertTrue(result.successful());
        assertSame(artifact, result.artifact());
        assertThrows(IllegalArgumentException.class,
                () -> new CompilationResult(true, List.of(), null));
    }

    @Test
    void artifactDeepCopiesByteArraysOnConstructionAndRead() {
        byte[] bytes = {1, 2, 3};
        CompiledArtifact artifact = new CompiledArtifact(
                "ws", CompilationTarget.JVM, "1.0.0", Map.of("Script", bytes));

        bytes[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, artifact.classBytes().get("Script"));

        byte[] returned = artifact.classBytes().get("Script");
        returned[1] = 88;
        assertArrayEquals(new byte[]{1, 2, 3}, artifact.classBytes().get("Script"));
        assertThrows(UnsupportedOperationException.class,
                () -> artifact.classBytes().put("Other", new byte[]{4}));
    }

    @Test
    void targetEnumRemainsExplicit() {
        assertEquals(Set.of(CompilationTarget.JVM), EnumSet.allOf(CompilationTarget.class));
        assertEquals("JVM", CompilationTarget.JVM.name());
    }
}
