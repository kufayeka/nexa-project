package nexa.framework.runtime.domain.scripting.bytecode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small stack VM. It intentionally knows nothing about Asset Manager, MQTT, Control, etc. */
public final class NexaBytecodeVm {
    public Object execute(NexaBytecodeProgram program, NexaBytecodeExecutionContext context) {
        Object[] locals = new Object[program.localCount()];
        Deque<Object> stack = new ArrayDeque<>();
        List<NexaBytecodeInstruction> code = program.instructions();
        int pc = 0;

        while (pc < code.size()) {
            NexaBytecodeInstruction instruction = code.get(pc++);
            switch (instruction.opcode()) {
                case CONST -> stack.push(program.constants().get((Integer) instruction.operands().get(0)));
                case LOAD_LOCAL -> stack.push(locals[(Integer) instruction.operands().get(0)]);
                case STORE_LOCAL -> locals[(Integer) instruction.operands().get(0)] = stack.pop();
                case LOAD_GLOBAL -> stack.push(context.global((String) instruction.operands().get(0)));
                case LOAD_PROPERTY -> {
                    String property = (String) instruction.operands().get(0);
                    stack.push(readProperty(stack.pop(), property));
                }
                case STORE_PROPERTY -> {
                    String property = (String) instruction.operands().get(0);
                    Object value = stack.pop();
                    Object target = stack.pop();
                    writeProperty(target, property, value);
                    stack.push(value);
                }
                case LOAD_INDEX -> {
                    Object index = stack.pop();
                    Object target = stack.pop();
                    stack.push(readIndex(target, index));
                }
                case STORE_INDEX -> {
                    Object value = stack.pop();
                    Object index = stack.pop();
                    Object target = stack.pop();
                    writeIndex(target, index, value);
                    stack.push(value);
                }
                case MAKE_ARRAY -> {
                    int count = (Integer) instruction.operands().get(0);
                    List<Object> values = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) values.add(0, stack.pop());
                    stack.push(values);
                }
                case MAKE_OBJECT -> {
                    int count = (Integer) instruction.operands().get(0);
                    Map<String, Object> object = new LinkedHashMap<>();
                    for (int i = 0; i < count; i++) {
                        Object value = stack.pop();
                        String key = (String) stack.pop();
                        object.put(key, value);
                    }
                    stack.push(object);
                }
                case ADD -> stack.push(binary(stack.pop(), stack.pop(), '+'));
                case SUB -> stack.push(binary(stack.pop(), stack.pop(), '-'));
                case MUL -> stack.push(binary(stack.pop(), stack.pop(), '*'));
                case DIV -> stack.push(binary(stack.pop(), stack.pop(), '/'));
                case MOD -> stack.push(binary(stack.pop(), stack.pop(), '%'));
                case NEGATE -> stack.push(negate(stack.pop()));
                case NOT -> stack.push(!truthy(stack.pop()));
                case EQUAL -> stack.push(java.util.Objects.equals(stack.pop(), stack.pop()));
                case NOT_EQUAL -> stack.push(!java.util.Objects.equals(stack.pop(), stack.pop()));
                case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
                    Object right = stack.pop();
                    Object left = stack.pop();
                    int c = compare(left, right);
                    switch (instruction.opcode()) {
                        case LESS -> stack.push(c < 0);
                        case LESS_EQUAL -> stack.push(c <= 0);
                        case GREATER -> stack.push(c > 0);
                        default -> stack.push(c >= 0);
                    }
                }
                case AND -> {
                    boolean right = truthy(stack.pop());
                    boolean left = truthy(stack.pop());
                    stack.push(left && right);
                }
                case OR -> {
                    boolean right = truthy(stack.pop());
                    boolean left = truthy(stack.pop());
                    stack.push(left || right);
                }
                case JUMP -> pc = (Integer) instruction.operands().get(0);
                case JUMP_IF_FALSE -> {
                    Object value = stack.pop();
                    if (!truthy(value)) pc = (Integer) instruction.operands().get(0);
                }
                case CALL_HOST -> {
                    String name = (String) instruction.operands().get(0);
                    int argc = (Integer) instruction.operands().get(1);
                    Object[] args = new Object[argc];
                    for (int i = argc - 1; i >= 0; i--) args[i] = stack.pop();
                    stack.push(context.callHost(name, args));
                }
                case POP -> stack.pop();
                case RETURN -> { return stack.isEmpty() ? null : stack.pop(); }
            }
        }
        return stack.isEmpty() ? null : stack.pop();
    }

    private static Object binary(Object right, Object left, char op) {
        if (op == '+' && (left instanceof String || right instanceof String)) return String.valueOf(left) + right;
        if (left instanceof Number l && right instanceof Number r) {
            if (l instanceof Double || r instanceof Double) return arithmeticDouble(l.doubleValue(), r.doubleValue(), op);
            if (l instanceof Float || r instanceof Float) return (float) arithmeticDouble(l.floatValue(), r.floatValue(), op);
            long value = arithmeticLong(l.longValue(), r.longValue(), op);
            if (l instanceof Integer || r instanceof Integer) return (int) value;
            if (l instanceof Short || r instanceof Short) return (short) value;
            if (l instanceof Byte || r instanceof Byte) return (byte) value;
            return value;
        }
        throw new IllegalArgumentException("Unsupported operands for " + op + ": " + left + ", " + right);
    }

    private static double arithmeticDouble(double l, double r, char op) {
        return switch (op) { case '+' -> l + r; case '-' -> l - r; case '*' -> l * r; case '/' -> l / r; case '%' -> l % r; default -> throw new IllegalArgumentException(); };
    }

    private static long arithmeticLong(long l, long r, char op) {
        return switch (op) { case '+' -> l + r; case '-' -> l - r; case '*' -> l * r; case '/' -> l / r; case '%' -> l % r; default -> throw new IllegalArgumentException(); };
    }

    private static Object negate(Object value) {
        if (value instanceof Double d) return -d;
        if (value instanceof Float f) return -f;
        if (value instanceof Number n) return -n.longValue();
        throw new IllegalArgumentException("Cannot negate " + value);
    }

    private static boolean truthy(Object value) {
        if (value == null || Boolean.FALSE.equals(value)) return false;
        if (value instanceof Number n) return n.doubleValue() != 0.0d;
        if (value instanceof String s) return !s.isEmpty();
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Object readProperty(Object target, String property) {
        if (target instanceof Map<?, ?> map) return map.get(property);
        throw new IllegalArgumentException("Cannot read property '" + property + "' from " + target);
    }

    @SuppressWarnings("unchecked")
    private static void writeProperty(Object target, String property, Object value) {
        if (target instanceof Map<?, ?> map) { ((Map<String, Object>) map).put(property, value); return; }
        throw new IllegalArgumentException("Cannot write property '" + property + "' on " + target);
    }

    private static Object readIndex(Object target, Object index) {
        if (target instanceof List<?> list) return list.get(((Number) index).intValue());
        if (target instanceof Map<?, ?> map) return map.get(index);
        if (target instanceof String s) return String.valueOf(s.charAt(((Number) index).intValue()));
        throw new IllegalArgumentException("Cannot index " + target);
    }

    @SuppressWarnings("unchecked")
    private static void writeIndex(Object target, Object index, Object value) {
        if (target instanceof List<?> list) { ((List<Object>) list).set(((Number) index).intValue(), value); return; }
        if (target instanceof Map<?, ?> map) { ((Map<Object, Object>) map).put(index, value); return; }
        throw new IllegalArgumentException("Cannot write index on " + target);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object left, Object right) {
        if (left instanceof Number l && right instanceof Number r) return Double.compare(l.doubleValue(), r.doubleValue());
        if (left instanceof Comparable l) return l.compareTo(right);
        throw new IllegalArgumentException("Values are not comparable");
    }
}
