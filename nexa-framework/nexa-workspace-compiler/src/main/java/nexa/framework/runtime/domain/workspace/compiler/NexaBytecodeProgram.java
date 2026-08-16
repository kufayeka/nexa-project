package nexa.framework.runtime.domain.workspace.compiler;

import java.util.List;

/** Immutable bytecode container. The runtime can consume this without parsing source. */
public record NexaBytecodeProgram(List<BytecodeInstruction> instructions, List<Object> constants) {
    public NexaBytecodeProgram {
        instructions = instructions == null ? List.of() : List.copyOf(instructions);
        constants = constants == null ? List.of() : List.copyOf(constants);
    }

    public static NexaBytecodeProgram empty() {
        return new NexaBytecodeProgram(List.of(), List.of());
    }
}
