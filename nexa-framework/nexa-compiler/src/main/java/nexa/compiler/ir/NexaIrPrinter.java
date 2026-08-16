package nexa.compiler.ir;

import java.util.*;

/** Human-readable deterministic representation used by compiler diagnostics and golden tests. */
public final class NexaIrPrinter {
    public String print(NexaIr.Program program) {
        Objects.requireNonNull(program, "program");
        StringBuilder out = new StringBuilder();
        out.append("nexa-ir v").append(program.irVersion()).append(' ').append(program.name()).append('\n');

        for (NexaIr.Function function : program.functions()) {
            out.append("function ").append(function.name()).append("() -> ")
                    .append(function.returnType().displayName()).append('\n');
            for (NexaIr.Block block : function.blocks()) {
                out.append("  block ").append(block.id()).append(':').append('\n');
                for (NexaIr.Instruction instruction : block.instructions()) {
                    out.append("    ").append(instruction(instruction)).append('\n');
                }
                out.append("    ").append(terminator(block.terminator())).append('\n');
            }
        }
        return out.toString();
    }

    private String instruction(NexaIr.Instruction i) {
        if (i instanceof NexaIr.Const x) return value(x.result()) + " = const " + String.valueOf(x.value());
        if (i instanceof NexaIr.LoadLocal x) return value(x.result()) + " = load_local " + x.name();
        if (i instanceof NexaIr.StoreLocal x) return "store_local " + x.name() + " <- " + value(x.value());
        if (i instanceof NexaIr.LoadField x) return value(x.result()) + " = field " + value(x.target()) + "." + x.field();
        if (i instanceof NexaIr.StoreField x) return "field " + value(x.target()) + "." + x.field() + " <- " + value(x.value());
        if (i instanceof NexaIr.LoadIndex x) return value(x.result()) + " = index " + value(x.target()) + "[" + value(x.index()) + "]";
        if (i instanceof NexaIr.StoreIndex x) return "index " + value(x.target()) + "[" + value(x.index()) + "] <- " + value(x.value());
        if (i instanceof NexaIr.ArrayCreate x) return value(x.result()) + " = array " + values(x.values());
        if (i instanceof NexaIr.ObjectCreate x) return value(x.result()) + " = object " + x.fields().keySet();
        if (i instanceof NexaIr.Unary x) return value(x.result()) + " = " + x.op() + ' ' + value(x.operand());
        if (i instanceof NexaIr.Binary x) return value(x.result()) + " = " + value(x.left()) + ' ' + x.op() + ' ' + value(x.right());
        if (i instanceof NexaIr.Call x) return value(x.result()) + " = call " + x.target() + values(x.args());
        if (i instanceof NexaIr.HostCall x) return value(x.result()) + " = host " + x.capability().qualifiedName() + values(x.args());
        if (i instanceof NexaIr.Iterate x) return value(x.result()) + " = iterate " + value(x.iterable());
        if (i instanceof NexaIr.IterHasNext x) return value(x.result()) + " = has_next " + value(x.iterator());
        if (i instanceof NexaIr.IterNext x) return value(x.result()) + " = next " + value(x.iterator());
        if (i instanceof NexaIr.Return x) return "return " + (x.value() == null ? "" : value(x.value()));
        return i.getClass().getSimpleName();
    }

    private String terminator(NexaIr.Terminator t) {
        if (t instanceof NexaIr.Jump x) return "jump block " + x.targetBlock();
        if (t instanceof NexaIr.Branch x) return "branch " + value(x.condition()) + " ? block " + x.trueBlock() + " : block " + x.falseBlock();
        return "stop";
    }

    private String value(NexaIr.Value value) {
        return "v" + value.id() + ':' + value.type().displayName();
    }

    private String values(Collection<NexaIr.Value> values) {
        return values.stream().map(this::value).toList().toString();
    }
}
