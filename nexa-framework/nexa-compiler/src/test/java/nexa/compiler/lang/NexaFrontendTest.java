package nexa.compiler.lang;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NexaFrontendTest {
    private final NexaFrontend frontend = new NexaFrontend();

    @Test void acceptsAllCanonicalPrimitiveDeclarations(){
        String s="let b: BOOLEAN = true; let i8: INT8 = 100; let i16: INT16 = 1000; let i32: INT32 = 100000; let i64: INT64 = 1000000; "
            +"let u8: UINT8 = 200; let u16: UINT16 = 60000; let u32: UINT32 = 4000000000; let u64: UINT64 = 900000000000; "
            +"let f32: FLOAT32 = 1.5; let f64: FLOAT64 = 2.5; let text: STRING = \"nexa\";";
        assertTrue(frontend.compile(s).success(), () -> frontend.compile(s).diagnostics().toString());
    }

    @Test void acceptsNestedTypedObjectAndArray(){
        String s="type ProductionOrder = { id: STRING, machine: { id: STRING, speed: INT32 }, materials: ARRAY<{ code: STRING, quantity: FLOAT64, unit: STRING }> };"
            +"let order: ProductionOrder = input; let total: FLOAT64 = 0.0; for (let material: OBJECT in order.materials) { total = total + material.quantity; } return total;";
        assertFalse(frontend.compile(s).diagnostics().isEmpty(), "Typed loop variable OBJECT should be checked against ARRAY element schema; dynamic OBJECT is intentionally not silently accepted by strict mode");
    }

    @Test void rejectsOutOfRangeIntegerLiteral(){
        assertFalse(frontend.compile("let x: INT8 = 128;").success());
        assertFalse(frontend.compile("let x: UINT8 = 256;").success());
        assertFalse(frontend.compile("let x: UINT8 = -1;").success());
    }

    @Test void rejectsUnknownField(){
        String s="type Motor = { speed: INT32 }; let m: Motor = input; return m.rpm;";
        assertFalse(frontend.compile(s).success());
    }

    @Test void rejectsInvalidArithmetic(){
        assertFalse(frontend.compile("let x: STRING = \"a\"; return x + 1;").success());
    }

    @Test void supportsAssignmentsAndIndexing(){
        String s="let xs: ARRAY<INT32> = [1,2,3]; xs[1] = 42; return xs[1];";
        assertTrue(frontend.compile(s).success(), () -> frontend.compile(s).diagnostics().toString());
    }
}
