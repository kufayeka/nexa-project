package nexa.compiler.lang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NexaConstTest {
    private final NexaFrontend frontend = new NexaFrontend();

    @Test
    void acceptsConstDeclaration() {
        var result = frontend.compile("const limit: INT32 = 100;");
        assertTrue(result.success(), () -> result.diagnostics().toString());
    }

    @Test
    void rejectsAssignmentToConst() {
        var result = frontend.compile("const limit: INT32 = 100; limit = 200;");
        assertFalse(result.success(), () -> "Expected const assignment to fail");
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("Cannot assign to constant")),
                () -> result.diagnostics().toString());
    }

    @Test
    void constCanBeReadNormally() {
        var result = frontend.compile("const limit: INT32 = 100; let copy: INT32 = limit;");
        assertTrue(result.success(), () -> result.diagnostics().toString());
    }

    @Test
    void letRemainsMutable() {
        var result = frontend.compile("let value: INT32 = 100; value = 200;");
        assertTrue(result.success(), () -> result.diagnostics().toString());
    }

    @Test
    void constRequiresInitializer() {
        var result = frontend.compile("const value: INT32;");
        assertFalse(result.success());
    }
}
