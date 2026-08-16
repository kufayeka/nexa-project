package nexa.compiler.lang;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Phase 5 language-level regression matrix.
 *
 * These tests intentionally exercise constructs that must survive the
 * frontend/type-checking boundary before they are lowered to IR.
 */
class NexaPhase5LanguageTest {
    private final NexaFrontend frontend = new NexaFrontend();

    private void compiles(String source) {
        var result = frontend.compile(source);
        assertTrue(result.success(), () -> result.diagnostics().toString());
    }

    private void rejects(String source) {
        var result = frontend.compile(source);
        assertFalse(result.success(), () -> "Expected rejection: " + result.diagnostics());
    }

    @Test
    void coversNestedExpressionsAndPrecedence() {
        compiles("""
                let a: INT32 = 2 + 3 * 4;
                let b: INT32 = (2 + 3) * 4;
                let c: BOOLEAN = a > 10 && a < 20 || false;
                let d: BOOLEAN = !(a == 0);
                """);
    }

    @Test
    void coversEveryWritableTargetKind() {
        compiles("""
                let obj: OBJECT = { value: 1 };
                let xs: ARRAY<INT32> = [1, 2, 3];
                obj.value = 2;
                xs[0] = 10;
                let x: INT32 = xs[0];
                """);
    }

    @Test
    void coversNestedObjectAndArrayAccess() {
        compiles("""
                let root: OBJECT = { child: { values: [10, 20] } };
                let value: OBJECT = root.child.values[1];
                """);
    }

    @Test
    void coversDynamicHostCallsAtAnyDepth() {
        compiles("""
                mqtt.publish("topic", 42);
                mqtt.client.publish("topic", 42);
                system.io.write("hello");
                foo(bar(baz(1)));
                """);
    }

    @Test
    void coversLoopsAndLoopScope() {
        compiles("""
                let xs: ARRAY<INT32> = [1, 2, 3];
                for (item: INT32 in xs) {
                    let doubled: INT32 = item * 2;
                    doubled = doubled + 1;
                }
                """);

        rejects("""
                let xs: ARRAY<INT32> = [1, 2, 3];
                for (item: INT32 in xs) { }
                let outside: INT32 = item;
                """);
    }

    @Test
    void coversConstAndMutableSemantics() {
        compiles("""
                const limit: INT32 = 100;
                let current: INT32 = 10;
                current = limit;
                """);

        rejects("""
                const limit: INT32 = 100;
                limit = 200;
                """);
    }

    @Test
    void coversNumericBoundariesAndConstantNarrowing() {
        compiles("""
                let a: INT8 = 127;
                let b: INT16 = 127 + 1;
                let c: INT32 = 1000 * 2;
                let d: FLOAT64 = 10 + 0.5;
                """);

        rejects("let x: INT8 = 128;");
        rejects("let x: INT32 = 2147483648;");
        rejects("let x: INT32 = 1.5;");
    }

    @Test
    void coversInvalidOperatorFamilies() {
        rejects("let x: BOOLEAN = true + false;");
        rejects("let x: BOOLEAN = true && 1;");
        rejects("let x: INT32 = 1 < 2;");
        rejects("let x: INT32 = !1;");
        rejects("let x: INT32 = 1 + true;");
    }

    @Test
    void coversTypedObjectDeclarationsAndFields() {
        compiles("""
                type Motor = { speed: FLOAT64, enabled: BOOLEAN };
                let motor: Motor = { speed: 12.5, enabled: true };
                let speed: FLOAT64 = motor.speed;
                motor.speed = 20.0;
                """);

        rejects("""
                type Motor = { speed: FLOAT64 };
                let motor: Motor = { speed: 12.5 };
                let missing: INT32 = motor.missing;
                """);
    }

    @Test
    void coversEmptyAndNestedTypedArrays() {
        compiles("""
                let empty: ARRAY<INT32> = [];
                let matrix: ARRAY<ARRAY<INT32>> = [[1, 2], [3, 4]];
                let x: INT32 = matrix[0][1];
                """);

        rejects("""
                let xs: ARRAY<INT32> = [1, true];
                """);
    }

    @Test
    void coversReturnAndExpressionStatements() {
        compiles("""
                let x: INT32 = 1;
                x + 2;
                return x;
                """);
    }
}
