package nexa.framework.runtime.domain.scripting.bytecode;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NexaBytecodeVmTest {
    @Test
    void executesConditionalBytecode() {
        NexaBytecodeProgram program = new NexaBytecodeCompiler().compile(
                "if.nexa",
                "var x = 10; if (x > 5) { return 99; } return 1;"
        );

        assertEquals(99L, new NexaBytecodeVm().execute(
                program,
                new NexaBytecodeExecutionContext(Map.of(), null)
        ));
    }

    @Test
    void preservesNullAsAValidRuntimeValue() {
        NexaBytecodeProgram program = new NexaBytecodeCompiler().compile(
                "null.nexa",
                "return null;"
        );

        assertEquals(null, new NexaBytecodeVm().execute(
                program,
                new NexaBytecodeExecutionContext(Map.of(), null)
        ));
    }
}
