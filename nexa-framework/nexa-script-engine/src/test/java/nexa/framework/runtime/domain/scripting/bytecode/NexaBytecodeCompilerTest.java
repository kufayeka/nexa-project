package nexa.framework.runtime.domain.scripting.bytecode;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NexaBytecodeCompilerTest {
    @Test
    void compilesArithmeticWithoutKeepingAstInArtifact() {
        NexaBytecodeProgram program = new NexaBytecodeCompiler().compile(
                "arithmetic.nexa",
                "var x = 10; return x * 2 + 5;"
        );

        assertEquals("arithmetic.nexa", program.sourceName());
        assertFalse(program.instructions().isEmpty());
        assertTrue(program.instructions().stream().noneMatch(i -> i.opcode().name().contains("AST")));
        assertEquals(1, program.localCount());

        Object result = new NexaBytecodeVm().execute(
                program,
                new NexaBytecodeExecutionContext(Map.of(), null)
        );
        assertEquals(25L, result);
    }

    @Test
    void bytecodeReadsNestedObjectsAndArrays() {
        NexaBytecodeProgram program = new NexaBytecodeCompiler().compile(
                "object.nexa",
                "var order = {machine: {speed: 120}, values: [2, 3]}; return order.machine.speed + order.values[1];"
        );

        Object result = new NexaBytecodeVm().execute(
                program,
                new NexaBytecodeExecutionContext(Map.of(), null)
        );
        assertEquals(123L, result);
    }

    @Test
    void bytecodeCallsHostWithoutEmbeddingPluginImplementation() {
        NexaBytecodeProgram program = new NexaBytecodeCompiler().compile(
                "host.nexa",
                "return readTag(20) + 1;"
        );

        Object result = new NexaBytecodeVm().execute(
                program,
                new NexaBytecodeExecutionContext(Map.of(), (name, args) -> {
                    assertEquals("readTag", name);
                    assertEquals(1, args.length);
                    return args[0];
                })
        );
        assertEquals(21L, result);
    }
}
