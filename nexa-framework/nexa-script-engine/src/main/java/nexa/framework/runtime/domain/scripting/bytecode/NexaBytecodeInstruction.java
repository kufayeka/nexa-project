package nexa.framework.runtime.domain.scripting.bytecode;

import java.util.List;
import java.util.Objects;

public record NexaBytecodeInstruction(NexaBytecodeOpcode opcode, List<Object> operands) {
    public NexaBytecodeInstruction {
        Objects.requireNonNull(opcode, "opcode");
        operands = operands == null ? List.of() : List.copyOf(operands);
    }

    public static NexaBytecodeInstruction of(NexaBytecodeOpcode opcode, Object... operands) {
        return new NexaBytecodeInstruction(opcode, List.of(operands));
    }
}
