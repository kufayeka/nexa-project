package nexa.framework.runtime.domain.workspace.compiler;

import java.util.List;
import java.util.Objects;

/** Immutable instruction used by the Phase-1 bytecode contract. */
public record BytecodeInstruction(BytecodeOpcode opcode, List<Object> operands) {
    public BytecodeInstruction {
        Objects.requireNonNull(opcode, "opcode");
        operands = operands == null ? List.of() : List.copyOf(operands);
    }

    public static BytecodeInstruction of(BytecodeOpcode opcode, Object... operands) {
        return new BytecodeInstruction(opcode, List.of(operands));
    }
}
