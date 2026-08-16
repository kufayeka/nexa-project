package nexa.framework.runtime.domain.scripting.bytecode;

import java.util.List;

/** Immutable compiled Nexa artifact. No source or AST is required for execution. */
public record NexaBytecodeProgram(
        String sourceName,
        List<NexaBytecodeInstruction> instructions,
        List<Object> constants,
        int localCount) {
    public NexaBytecodeProgram {
        sourceName = sourceName == null ? "<script>" : sourceName;
        instructions = instructions == null ? List.of() : List.copyOf(instructions);
        constants = constants == null ? List.of() : List.copyOf(constants);
        if (localCount < 0) throw new IllegalArgumentException("localCount must be >= 0");
    }
}
