package nexa.compiler.ir;

import java.util.*;

/**
 * Small, deterministic, backend-neutral optimizer for Nexa IR.
 *
 * The optimizer is deliberately conservative: it folds only operations whose
 * semantics are fully known at compile time. Host calls, loads/stores, array
 * operations and object operations are never speculated or reordered.
 */
public final class NexaIrOptimizer {

    public NexaIr.Program optimize(NexaIr.Program program) {
        Objects.requireNonNull(program, "program");

        List<NexaIr.Function> functions = program.functions().stream()
                .map(this::optimizeFunction)
                .toList();

        return new NexaIr.Program(
                program.name(),
                functions,
                program.types(),
                program.irVersion());
    }

    private NexaIr.Function optimizeFunction(NexaIr.Function function) {
        List<NexaIr.Block> blocks = function.blocks().stream()
                .map(this::optimizeBlock)
                .toList();

        return new NexaIr.Function(
                function.name(),
                function.locals(),
                blocks,
                function.returnType(),
                function.parameters());
    }

    private NexaIr.Block optimizeBlock(NexaIr.Block block) {
        Map<Integer, ConstValue> constants = new HashMap<>();
        List<NexaIr.Instruction> optimized = new ArrayList<>(block.instructions().size());

        for (NexaIr.Instruction instruction : block.instructions()) {
            NexaIr.Instruction replacement = fold(instruction, constants);
            optimized.add(replacement);

            if (replacement instanceof NexaIr.Const constant) {
                constants.put(constant.result().id(), new ConstValue(constant.value(), constant.result().type()));
            } else if (replacement.result() != null) {
                constants.remove(replacement.result().id());
            }
        }

        return new NexaIr.Block(block.id(), optimized, block.terminator());
    }

    private NexaIr.Instruction fold(
            NexaIr.Instruction instruction,
            Map<Integer, ConstValue> constants) {

        if (!(instruction instanceof NexaIr.Binary binary)) {
            return instruction;
        }

        ConstValue left = constants.get(binary.left().id());
        ConstValue right = constants.get(binary.right().id());
        if (left == null || right == null) {
            return instruction;
        }

        Object value = evaluate(binary.op(), left.value(), right.value());
        if (value == NO_FOLD) {
            return instruction;
        }

        return new NexaIr.Const(binary.result(), value, binary.span());
    }

    private static final Object NO_FOLD = new Object();

    private Object evaluate(String op, Object left, Object right) {
        if (left instanceof Number l && right instanceof Number r) {
            double a = l.doubleValue();
            double b = r.doubleValue();

            return switch (op) {
                case "+" -> numericResult(left, right, a + b);
                case "-" -> numericResult(left, right, a - b);
                case "*" -> numericResult(left, right, a * b);
                case "/" -> b == 0.0 ? NO_FOLD : numericResult(left, right, a / b);
                case "==" -> Double.compare(a, b) == 0;
                case "!=" -> Double.compare(a, b) != 0;
                case "<" -> a < b;
                case "<=" -> a <= b;
                case ">" -> a > b;
                case ">=" -> a >= b;
                default -> NO_FOLD;
            };
        }

        if (left instanceof Boolean a && right instanceof Boolean b) {
            return switch (op) {
                case "&&" -> a && b;
                case "||" -> a || b;
                case "==" -> a.equals(b);
                case "!=" -> !a.equals(b);
                default -> NO_FOLD;
            };
        }

        if (left instanceof String a && right instanceof String b) {
            return switch (op) {
                case "==" -> a.equals(b);
                case "!=" -> !a.equals(b);
                case "+" -> a + b;
                default -> NO_FOLD;
            };
        }

        return NO_FOLD;
    }

    private Object numericResult(Object left, Object right, double value) {
        if (left instanceof Float || right instanceof Float
                || left instanceof Double || right instanceof Double) {
            return value;
        }

        long rounded = Math.round(value);
        if (left instanceof Integer || right instanceof Integer) return (int) rounded;
        if (left instanceof Short || right instanceof Short) return (short) rounded;
        if (left instanceof Byte || right instanceof Byte) return (byte) rounded;
        return rounded;
    }

    private record ConstValue(Object value, nexa.compiler.lang.NexaType type) {}
}
