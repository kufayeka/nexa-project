package nexa.compiler.lang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NexaFrontendTest {

    private final NexaFrontend frontend = new NexaFrontend();

    private void assertCompiles(String source) {
        var result = frontend.compile(source);

        assertTrue(
                result.success(),
                () -> result.diagnostics().toString());
    }

    private void assertRejects(String source) {
        var result = frontend.compile(source);

        assertFalse(
                result.success(),
                () -> "Expected compilation to fail, but it succeeded");
    }

    // ============================================================
    // Primitive types
    // ============================================================

    @Test
    void acceptsAllCanonicalPrimitiveDeclarations() {
        String s = """
                let b: BOOLEAN = true;

                let i8: INT8 = 100;
                let i16: INT16 = 1000;
                let i32: INT32 = 100000;
                let i64: INT64 = 1000000;

                let u8: UINT8 = 200;
                let u16: UINT16 = 60000;
                let u32: UINT32 = 4000000000;
                let u64: UINT64 = 900000000000;

                let f32: FLOAT32 = 1.5;
                let f64: FLOAT64 = 2.5;

                let text: STRING = "nexa";
                """;

        assertCompiles(s);
    }

    @Test
    void acceptsBooleanLiterals() {
        assertCompiles("""
                    let a: BOOLEAN = true;
                    let b: BOOLEAN = false;
                """);
    }

    @Test
    void acceptsStringLiterals() {
        assertCompiles("""
                    let a: STRING = "hello";
                    let b: STRING = "Nexa Runtime";
                """);
    }

    // ============================================================
    // Integer range validation
    // ============================================================

    @Test
    void acceptsIntegerBoundaries() {
        assertCompiles("""
                    let a: INT8 = -128;
                    let b: INT8 = 127;

                    let c: INT16 = -32768;
                    let d: INT16 = 32767;

                    let e: INT32 = -2147483648;
                    let f: INT32 = 2147483647;

                    let g: UINT8 = 0;
                    let h: UINT8 = 255;

                    let i: UINT16 = 0;
                    let j: UINT16 = 65535;

                    let k: UINT32 = 0;
                    let l: UINT32 = 4294967295;
                """);
    }

    @Test
    void rejectsOutOfRangeIntegerLiteral() {
        assertRejects("""
                    let x: INT8 = 128;
                """);

        assertRejects("""
                    let x: INT8 = -129;
                """);

        assertRejects("""
                    let x: UINT8 = 256;
                """);

        assertRejects("""
                    let x: UINT8 = -1;
                """);

        assertRejects("""
                    let x: UINT16 = 65536;
                """);

        assertRejects("""
                    let x: UINT32 = 4294967296;
                """);
    }

    // ============================================================
    // Numeric conversion
    // ============================================================

    @Test
    void acceptsNumericWidening() {
        assertCompiles("""
                    let a: INT8 = 10;
                    let b: INT16 = a;
                    let c: INT32 = b;
                    let d: INT64 = c;
                """);
    }

    @Test
    void acceptsFloatWidening() {
        assertCompiles("""
                    let a: FLOAT32 = 1.5;
                    let b: FLOAT64 = a;
                """);
    }

    @Test
    void acceptsIntegerToFloatWidening() {
        assertCompiles("""
                    let a: INT32 = 100;
                    let b: FLOAT64 = a;
                """);
    }

    @Test
    void rejectsNarrowingConversion() {
        assertRejects("""
                    let x: INT64 = 1000;
                    let y: INT8 = x;
                """);
    }

    @Test
    void rejectsFloatToIntegerConversion() {
        assertRejects("""
                    let x: FLOAT64 = 10.5;
                    let y: INT32 = x;
                """);
    }

    // ============================================================
    // Variables
    // ============================================================

    @Test
    void supportsVariableDeclaration() {
        assertCompiles("""
                    let x: INT32 = 10;
                    let y: INT32 = x;
                """);
    }

    @Test
    void rejectsUnknownVariable() {
        assertRejects("""
                    return unknownVariable;
                """);
    }

    @Test
    void supportsVariableAssignment() {
        assertCompiles("""
                    let x: INT32 = 10;
                    x = 20;
                """);
    }

    @Test
    void rejectsInvalidAssignment() {
        assertRejects("""
                    let x: INT32 = 10;
                    x = "hello";
                """);
    }

    // ============================================================
    // Arithmetic
    // ============================================================

    @Test
    void supportsArithmeticOperators() {
        assertCompiles("""
                    let a: INT32 = 10 + 5;
                    let b: INT32 = 10 - 5;
                    let c: INT32 = 10 * 5;
                    let d: INT32 = 10 / 5;
                """);
    }

    @Test
    void supportsUnaryNumericOperators() {
        assertCompiles("""
                    let a: INT32 = +10;
                    let b: INT32 = -10;
                """);
    }

    @Test
    void respectsArithmeticPrecedence() {
        assertCompiles("""
                    let a: INT32 = 10 + 2 * 3;
                    let b: INT32 = (10 + 2) * 3;
                    let c: INT32 = 100 / 5 - 3;
                """);
    }

    @Test
    void rejectsInvalidArithmetic() {
        assertRejects("""
                    let x: STRING = "a";
                    return x + 1;
                """);
    }

    @Test
    void rejectsArithmeticWithBoolean() {
        assertRejects("""
                    let x: BOOLEAN = true;
                    let y: INT32 = 10;
                    return x + y;
                """);
    }

    // ============================================================
    // Boolean logic
    // ============================================================

    @Test
    void supportsBooleanOperators() {
        assertCompiles("""
                    let a: BOOLEAN = true;
                    let b: BOOLEAN = false;

                    let c: BOOLEAN = a && b;
                    let d: BOOLEAN = a || b;
                    let e: BOOLEAN = !a;
                """);
    }

    @Test
    void rejectsInvalidBooleanOperands() {
        assertRejects("""
                    let x: BOOLEAN = 10 && true;
                """);

        assertRejects("""
                    let x: BOOLEAN = true || 10;
                """);

        assertRejects("""
                    let x: BOOLEAN = !10;
                """);
    }

    // ============================================================
    // Comparisons
    // ============================================================

    @Test
    void supportsComparisons() {
        assertCompiles("""
                    let a: BOOLEAN = 10 == 10;
                    let b: BOOLEAN = 10 != 5;
                    let c: BOOLEAN = 10 < 20;
                    let d: BOOLEAN = 20 <= 20;
                    let e: BOOLEAN = 30 > 10;
                    let f: BOOLEAN = 30 >= 30;
                """);
    }

    @Test
    void supportsNumericComparisons() {
        assertCompiles("""
                    let a: INT32 = 10;
                    let b: INT64 = 20;

                    let c: BOOLEAN = a < b;
                    let d: BOOLEAN = a == b;
                    let e: BOOLEAN = a != b;
                """);
    }

    @Test
    void rejectsInvalidComparison() {
        assertRejects("""
                    let x: BOOLEAN = "hello" < 10;
                """);
    }

    // ============================================================
    // Arrays
    // ============================================================

    @Test
    void supportsTypedArrays() {
        assertCompiles("""
                    let xs: ARRAY<INT32> = [1, 2, 3];
                """);
    }

    @Test
    void supportsEmptyArray() {
        assertCompiles("""
                    let xs: ARRAY<INT32> = [];
                """);
    }

    @Test
    void supportsNestedArrays() {
        assertCompiles("""
                    let matrix: ARRAY<ARRAY<INT32>> = [
                        [1, 2],
                        [3, 4]
                    ];
                """);
    }

    @Test
    void supportsNestedArrayIndexing() {
        assertCompiles("""
                    let matrix: ARRAY<ARRAY<INT32>> = [
                        [1, 2],
                        [3, 4]
                    ];

                    let x: INT32 = matrix[0][1];
                """);
    }

    @Test
    void supportsArrayAssignmentAndIndexing() {
        assertCompiles("""
                    let xs: ARRAY<INT32> = [1, 2, 3];

                    xs[1] = 42;

                    return xs[1];
                """);
    }

    @Test
    void rejectsMixedArrayTypes() {
        assertRejects("""
                    let xs: ARRAY<INT32> = [1, 2, "hello"];
                """);
    }

    @Test
    void rejectsWrongArrayElementType() {
        assertRejects("""
                    let xs: ARRAY<STRING> = [1, 2, 3];
                """);
    }

    // ============================================================
    // Objects
    // ============================================================

    @Test
    void supportsObjectLiteral() {
        assertCompiles("""
                    let motor: OBJECT = {
                        speed: 1200,
                        running: true,
                        name: "M1"
                    };
                """);
    }

    @Test
    void supportsNestedObjectLiteral() {
        assertCompiles("""
                    let motor: OBJECT = {
                        id: "M1",
                        config: {
                            speed: 1200,
                            enabled: true
                        }
                    };
                """);
    }

    @Test
    void supportsObjectFieldAccess() {
        assertCompiles("""
                    let motor: OBJECT = {
                        speed: 1200
                    };

                    let speed: OBJECT = motor.speed;
                """);
    }

    // ============================================================
    // User-defined types
    // ============================================================

    @Test
    void supportsUserDefinedTypes() {
        assertCompiles("""
                    type Motor = {
                        id: STRING,
                        speed: INT32,
                        running: BOOLEAN
                    };

                    let motor: Motor = input;
                """);
    }

    @Test
    void supportsTypedFieldAccess() {
        assertCompiles("""
                    type Motor = {
                        id: STRING,
                        speed: INT32
                    };

                    let motor: Motor = input;

                    let speed: INT32 = motor.speed;
                    let id: STRING = motor.id;
                """);
    }

    @Test
    void rejectsUnknownField() {
        assertRejects("""
                    type Motor = {
                        speed: INT32
                    };

                    let motor: Motor = input;

                    return motor.rpm;
                """);
    }

    @Test
    void rejectsWrongFieldType() {
        assertRejects("""
                    type Motor = {
                        speed: INT32
                    };

                    let motor: Motor = input;

                    let speed: STRING = motor.speed;
                """);
    }

    @Test
    void rejectsDuplicateType() {
        assertRejects("""
                    type Motor = {
                        speed: INT32
                    };

                    type Motor = {
                        rpm: INT32
                    };
                """);
    }

    @Test
    void rejectsUnknownType() {
        assertRejects("""
                    let motor: UnknownMotor = input;
                """);
    }

    // ============================================================
    // Complex nested types
    // ============================================================

    @Test
    void supportsNestedTypedObjectAndArray() {
        String s = """
                    type ProductionOrder = {
                        id: STRING,
                        machine: {
                            id: STRING,
                            speed: INT32
                        },
                        materials: ARRAY<{
                            code: STRING,
                            quantity: FLOAT64,
                            unit: STRING
                        }>
                    };

                    let order: ProductionOrder = input;
                """;

        assertCompiles(s);
    }

    @Test
    void supportsNestedTypedFieldAccess() {
        assertCompiles("""
                    type ProductionOrder = {
                        id: STRING,
                        machine: {
                            id: STRING,
                            speed: INT32
                        }
                    };

                    let order: ProductionOrder = input;

                    let machineId: STRING = order.machine.id;
                    let speed: INT32 = order.machine.speed;
                """);
    }

    // ============================================================
    // Loops
    // ============================================================

    @Test
    void supportsArrayIteration() {
        assertCompiles("""
                    let xs: ARRAY<INT32> = [1, 2, 3];

                    for (let x: INT32 in xs) {
                        let y: INT32 = x + 1;
                    }
                """);
    }

    @Test
    void rejectsNonArrayIteration() {
        assertRejects("""
                    let x: INT32 = 10;

                    for (let value: INT32 in x) {
                    }
                """);
    }

    @Test
    void rejectsWrongLoopVariableType() {
        assertRejects("""
                    let xs: ARRAY<INT32> = [1, 2, 3];

                    for (let value: STRING in xs) {
                    }
                """);
    }

    @Test
    void rejectsDynamicObjectLoopVariableForTypedArray() {
        String s = """
                    type ProductionOrder = {
                        materials: ARRAY<{
                            code: STRING,
                            quantity: FLOAT64
                        }>
                    };

                    let order: ProductionOrder = input;

                    for (let material: OBJECT in order.materials) {
                        let quantity: FLOAT64 = material.quantity;
                    }
                """;

        assertRejects(s);
    }

    // ============================================================
    // Scope
    // ============================================================

    @Test
    void loopVariableIsAvailableInsideLoop() {
        assertCompiles("""
                    let xs: ARRAY<INT32> = [1, 2, 3];

                    for (let x: INT32 in xs) {
                        let y: INT32 = x + 1;
                    }
                """);
    }

    @Test
    void loopVariableDoesNotEscapeScope() {
        assertRejects("""
                    let xs: ARRAY<INT32> = [1, 2, 3];

                    for (let x: INT32 in xs) {
                    }

                    return x;
                """);
    }

    // ============================================================
    // self / input
    // ============================================================

    @Test
    void supportsInputGlobal() {
        assertCompiles("""
                    let value: OBJECT = input;
                """);
    }

    @Test
    void supportsSelfGlobal() {
        assertCompiles("""
                    let value: OBJECT = self;
                """);
    }

    // ============================================================
    // Calls
    // ============================================================

    @Test
    void supportsBasicCalls() {
        assertCompiles("""
                    let result: OBJECT = someFunction(10, "hello", true);
                """);
    }

    @Test
    void supportsNestedCalls() {
        assertCompiles("""
                    let result: OBJECT = foo(bar(10), baz("hello"));
                """);
    }

    // ============================================================
    // Realistic automation script
    // ============================================================

    @Test
    void supportsRealisticAutomationScript() {
        String source = """
                    type Motor = {
                        id: STRING,
                        speed: INT32,
                        running: BOOLEAN
                    };

                    type ProductionOrder = {
                        id: STRING,
                        quantity: INT32,
                        motor: Motor
                    };

                    let order: ProductionOrder = input;

                    let targetSpeed: INT32 = order.motor.speed;

                    let running: BOOLEAN = order.motor.running;

                    let remaining: INT32 = order.quantity - 10;

                    let result: OBJECT = {
                        orderId: order.id,
                        speed: targetSpeed,
                        running: running,
                        remaining: remaining
                    };

                    return result;
                """;

        assertCompiles(source);
    }

    @Test
    void supportsRealisticArrayProcessing() {
        String source = """
                    type Material = {
                        code: STRING,
                        quantity: FLOAT64
                    };

                    type ProductionOrder = {
                        id: STRING,
                        materials: ARRAY<Material>
                    };

                    let order: ProductionOrder = input;

                    let total: FLOAT64 = 0.0;

                    for (let material: Material in order.materials) {
                        total = total + material.quantity;
                    }

                    return total;
                """;

        assertCompiles(source);
    }

    // ============================================================
    // Complex expression
    // ============================================================

    @Test
    void supportsComplexBooleanExpression() {
        assertCompiles("""
                    let speed: INT32 = 100;
                    let running: BOOLEAN = true;

                    let valid: BOOLEAN =
                        running &&
                        speed > 50 &&
                        speed < 200;
                """);
    }

    @Test
    void supportsComplexArithmeticExpression() {
        assertCompiles("""
                    let a: FLOAT64 = 10.0;
                    let b: FLOAT64 = 20.0;
                    let c: FLOAT64 = 5.0;

                    let result: FLOAT64 =
                        (a + b) * c / 2.0;
                """);
    }
}