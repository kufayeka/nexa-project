package nexa.compiler.ir;

import java.util.*;

import nexa.compiler.lang.NexaType;

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
        return new NexaIr.Program(
                program.name(),
                program.functions().stream().map(this::optimizeFunction).toList(),
                program.types(),
                program.irVersion());
    }

    private NexaIr.Function optimizeFunction(NexaIr.Function function) {
        return new NexaIr.Function(
                function.name(),
                function.locals(),
                function.blocks().stream().map(this::optimizeBlock).toList(),
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

        if (!(instruction instanceof NexaIr.Binary binary)) return instruction;

        ConstValue left = constants.get(binary.left().id());
        ConstValue right = constants.get(binary.right().id());
        if (left == null || right == null) return instruction;

        Object folded = evaluate(
                binary.op(),
                left,
                right,
                binary.result().type());

        if (folded == NO_FOLD) return instruction;
        return new NexaIr.Const(binary.result(), folded, binary.span());
    }

    private static final Object NO_FOLD = new Object();

    private Object evaluate(
            String op,
            ConstValue left,
            ConstValue right,
            NexaType resultType) {

        Object a = left.value();
        Object b = right.value();

        if (a instanceof Number leftNumber && b instanceof Number rightNumber) {
            boolean floating = resultType.displayName().startsWith("FLOAT");

            if (floating) {
                double x = leftNumber.doubleValue();
                double y = rightNumber.doubleValue();
                return switch (op) {
                    case "+" -> x + y;
                    case "-" -> x - y;
                    case "*" -> x * y;
                    case "/" -> y == 0.0 ? NO_FOLD : x / y;
                    case "==" -> Double.compare(x, y) == 0;
                    case "!=" -> Double.compare(x, y) != 0;
                    case "<" -> x < y;
                    case "<=" -> x <= y;
                    case ">" -> x > y;
                    case ">=" -> x >= y;
                    default -> NO_FOLD;
                };
            }

            long x = leftNumber.longValue();
            long y = rightNumber.longValue();
            try {
                return switch (op) {
                    case "+" -> Math.addExact(x, y);
                    case "-" -> Math.subtractExact(x, y);
                    case "*" -> Math.multiplyExact(x, y);
                    // Do not fold integer division unless the result is exact;
                    // this keeps the optimizer independent of the backend's
                    // integer-division rounding semantics.
                    case "/" -> y == 0 || x % y != 0 ? NO_FOLD : x / y;
                    case "==" -> x == y;
                    case "!=" -> x != y;
                    case "<" -> x < y;
                    case "<=" -> x <= y;
                    case ">" -> x > y;
                    case ">=" -> x >= y;
                    default -> NO_FOLD;
                };
            } catch (ArithmeticException overflow) {
                return NO_FOLD;
            }
        }

        if (a instanceof Boolean leftBoolean && b instanceof Boolean rightBoolean) {
            return switch (op) {
                case "&&" -> leftBoolean && rightBoolean;
                case "||" -> leftBoolean || rightBoolean;
                case "==" -> leftBoolean.equals(rightBoolean);
                case "!=" -> !leftBoolean.equals(rightBoolean);
                default -> NO_FOLD;
            };
        }

        if (a instanceof String leftString && b instanceof String rightString) {
            return switch (op) {
                case "==" -> leftString.equals(rightString);
                case "!=" -> !leftString.equals(rightString);
                case "+" -> leftString + rightString;
                default -> NO_FOLD;
            };
        }

        return NO_FOLD;
    }

    private record ConstValue(Object value, NexaType type) {}
}
