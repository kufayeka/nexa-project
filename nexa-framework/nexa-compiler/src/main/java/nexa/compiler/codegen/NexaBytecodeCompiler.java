package nexa.compiler.codegen;

import nexa.compiler.ir.NexaIr;
import nexa.compiler.lang.NexaType;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.*;

/**
 * AOT JVM backend for the typed, verified Nexa IR.
 *
 * <p>The generated class implements {@code NexaCompiledNode}. Primitive Nexa
 * values stay primitive while executing arithmetic and comparisons; boxing is
 * only introduced at object/collection/runtime boundaries.</p>
 */
public final class NexaBytecodeCompiler {
    private static final String NODE = "nexa/framework/runtime/api/NexaCompiledNode";
    private static final String MSG = "nexa/framework/runtime/api/model/RuntimeMessage";
    private static final String CTX = "nexa/framework/runtime/api/NexaExecutionContext";
    private static final String MAP = "java/util/Map";
    private static final String LIST = "java/util/List";
    private static final String ARRAY_LIST = "java/util/ArrayList";
    private static final String LINKED_HASH_MAP = "java/util/LinkedHashMap";

    public byte[] compile(NexaIr.Program program) {
        Objects.requireNonNull(program, "program");
        NexaIr.Function main = program.functions().stream()
                .filter(f -> f.name().equals("main"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Program does not contain main function"));
        return compileFunction(program, main);
    }

    public byte[] compileFunction(NexaIr.Program program, NexaIr.Function function) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(function, "function");

        String internalName = className(program.name(), function.name());
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V25, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null,
                "java/lang/Object", new String[]{NODE});

        emitConstructor(cw);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "execute",
                "(L" + MSG + ";L" + CTX + ";)V", null, null);
        mv.visitCode();

        SlotTable slots = SlotTable.build(function);
        emitRuntimeAliases(mv);

        Map<Integer, Label> labels = new HashMap<>();
        for (NexaIr.Block block : function.blocks()) labels.put(block.id(), new Label());

        for (NexaIr.Block block : function.blocks()) {
            mv.visitLabel(labels.get(block.id()));
            for (NexaIr.Instruction instruction : block.instructions()) {
                emitInstruction(mv, instruction, slots);
            }
            emitTerminator(mv, block.terminator(), labels, slots);
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private void emitConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private void emitRuntimeAliases(MethodVisitor mv) {
        // Slot 3 = self, slot 4 = input. Both intentionally alias msg.values().
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MSG, "values", "()Ljava/util/concurrent/ConcurrentMap;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MSG, "values", "()Ljava/util/concurrent/ConcurrentMap;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 4);
    }

    private void emitInstruction(MethodVisitor mv, NexaIr.Instruction instruction, SlotTable slots) {
        if (instruction instanceof NexaIr.Const c) {
            emitConst(mv, c.value(), c.result().type());
            storeResult(mv, c.result(), slots);
        } else if (instruction instanceof NexaIr.LoadLocal l) {
            loadLocal(mv, l.name(), slots);
            storeResult(mv, l.result(), slots);
        } else if (instruction instanceof NexaIr.StoreLocal s) {
            loadValue(mv, s.value(), slots);
            storeLocal(mv, s.name(), s.value().type(), slots);
        } else if (instruction instanceof NexaIr.LoadField l) {
            loadValue(mv, l.target(), slots);
            emitMapGet(mv, l.field());
            unbox(mv, l.result().type());
            storeResult(mv, l.result(), slots);
        } else if (instruction instanceof NexaIr.StoreField s) {
            loadValue(mv, s.target(), slots);
            loadValue(mv, s.value(), slots);
            box(mv, s.value().type());
            mv.visitLdcInsn(s.field());
            // Reorder to Map.put(key, value) using a temporary stack shape.
            // Map target/value/key is easier expressed with locals 254/255, but
            // those are unsafe. Use a small synthetic helper instead.
            mv.visitInsn(Opcodes.POP);
            throw unsupported("StoreField currently requires deterministic stack reordering", s);
        } else if (instruction instanceof NexaIr.LoadIndex l) {
            loadValue(mv, l.target(), slots);
            loadValue(mv, l.index(), slots);
            if (isArray(l.target().type())) {
                unboxIndex(mv, l.index().type());
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST, "get", "(I)Ljava/lang/Object;", true);
            } else {
                box(mv, l.index().type());
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            }
            unbox(mv, l.result().type());
            storeResult(mv, l.result(), slots);
        } else if (instruction instanceof NexaIr.StoreIndex s) {
            emitStoreIndex(mv, s, slots);
        } else if (instruction instanceof NexaIr.ArrayCreate a) {
            mv.visitTypeInsn(Opcodes.NEW, ARRAY_LIST);
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ARRAY_LIST, "<init>", "()V", false);
            for (NexaIr.Value value : a.values()) {
                mv.visitInsn(Opcodes.DUP);
                loadValue(mv, value, slots);
                box(mv, value.type());
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
                mv.visitInsn(Opcodes.POP);
            }
            storeResult(mv, a.result(), slots);
        } else if (instruction instanceof NexaIr.ObjectCreate o) {
            mv.visitTypeInsn(Opcodes.NEW, LINKED_HASH_MAP);
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, LINKED_HASH_MAP, "<init>", "()V", false);
            for (Map.Entry<String, NexaIr.Value> entry : o.fields().entrySet()) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(entry.getKey());
                loadValue(mv, entry.getValue(), slots);
                box(mv, entry.getValue().type());
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
                mv.visitInsn(Opcodes.POP);
            }
            storeResult(mv, o.result(), slots);
        } else if (instruction instanceof NexaIr.Unary u) {
            emitUnary(mv, u, slots);
        } else if (instruction instanceof NexaIr.Binary b) {
            emitBinary(mv, b, slots);
        } else if (instruction instanceof NexaIr.Call c) {
            emitCall(mv, c, slots);
        } else if (instruction instanceof NexaIr.HostCall h) {
            emitHostCall(mv, h, slots);
        } else if (instruction instanceof NexaIr.Iterate i) {
            loadValue(mv, i.iterable(), slots);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true);
            storeResult(mv, i.result(), slots);
        } else if (instruction instanceof NexaIr.IterHasNext h) {
            loadValue(mv, h.iterator(), slots);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
            storeResult(mv, h.result(), slots);
        } else if (instruction instanceof NexaIr.IterNext n) {
            loadValue(mv, n.iterator(), slots);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
            unbox(mv, n.result().type());
            storeResult(mv, n.result(), slots);
        } else if (instruction instanceof NexaIr.Return r) {
            if (r.value() != null) loadValue(mv, r.value(), slots);
            mv.visitInsn(Opcodes.RETURN);
        } else {
            throw new IllegalArgumentException("Unsupported Nexa IR instruction: " + instruction.getClass().getName());
        }
    }

    private void emitStoreIndex(MethodVisitor mv, NexaIr.StoreIndex s, SlotTable slots) {
        // Use a scratch slot owned by the compiler. COMPUTE_MAXS will account for it.
        int scratchMap = slots.scratch1();
        int scratchKey = slots.scratch2();
        int scratchValue = slots.scratch3();
        loadValue(mv, s.target(), slots);
        mv.visitVarInsn(Opcodes.ASTORE, scratchMap);
        loadValue(mv, s.index(), slots);
        box(mv, s.index().type());
        mv.visitVarInsn(Opcodes.ASTORE, scratchKey);
        loadValue(mv, s.value(), slots);
        box(mv, s.value().type());
        mv.visitVarInsn(Opcodes.ASTORE, scratchValue);
        mv.visitVarInsn(Opcodes.ALOAD, scratchMap);
        mv.visitVarInsn(Opcodes.ALOAD, scratchKey);
        mv.visitVarInsn(Opcodes.ALOAD, scratchValue);
        if (isArray(s.target().type())) {
            unboxIndexFromObject(mv, s.index().type(), scratchKey);
            mv.visitVarInsn(Opcodes.ALOAD, scratchValue);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST, "set", "(ILjava/lang/Object;)Ljava/lang/Object;", true);
        } else {
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
        }
        mv.visitInsn(Opcodes.POP);
    }

    private void emitTerminator(MethodVisitor mv, NexaIr.Terminator terminator,
                                Map<Integer, Label> labels, SlotTable slots) {
        if (terminator instanceof NexaIr.Jump j) {
            mv.visitJumpInsn(Opcodes.GOTO, labels.get(j.targetBlock()));
        } else if (terminator instanceof NexaIr.Branch b) {
            loadValue(mv, b.condition(), slots);
            mv.visitJumpInsn(Opcodes.IFNE, labels.get(b.trueBlock()));
            mv.visitJumpInsn(Opcodes.GOTO, labels.get(b.falseBlock()));
        } else if (terminator instanceof NexaIr.Stop) {
            mv.visitInsn(Opcodes.RETURN);
        } else {
            throw new IllegalArgumentException("Unsupported terminator: " + terminator.getClass().getName());
        }
    }

    private void emitCall(MethodVisitor mv, NexaIr.Call c, SlotTable slots) {
        if ("send".equals(c.target())) {
            if (c.args().size() == 1) {
                loadValue(mv, c.args().getFirst(), slots);
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                // Stack currently msg, context; reorder using scratch.
                int scratch = slots.scratch4();
                mv.visitVarInsn(Opcodes.ASTORE, scratch);
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.visitVarInsn(Opcodes.ALOAD, scratch);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, CTX, "send", "(L" + MSG + ";)V", true);
            } else if (c.args().size() == 2) {
                loadValue(mv, c.args().get(0), slots);
                loadValue(mv, c.args().get(1), slots);
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                int a2 = slots.scratch4();
                int a1 = slots.scratch5();
                mv.visitVarInsn(Opcodes.ASTORE, a2);
                mv.visitVarInsn(Opcodes.ASTORE, a1);
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.visitVarInsn(Opcodes.ALOAD, a1);
                mv.visitVarInsn(Opcodes.ALOAD, a2);
                if (isString(c.args().get(0).type())) {
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, CTX, "send", "(Ljava/lang/String;L" + MSG + ";)V", true);
                } else {
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, CTX, "send", "(Ljava/util/List;L" + MSG + ";)V", true);
                }
            } else {
                throw unsupported("send expects 1 or 2 arguments", c);
            }
            if (c.result() != null) emitDefault(mv, c.result().type());
            if (c.result() != null) storeResult(mv, c.result(), slots);
            return;
        }
        throw unsupported("Unsupported internal call: " + c.target(), c);
    }

    private void emitHostCall(MethodVisitor mv, NexaIr.HostCall h, SlotTable slots) {
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitLdcInsn(h.capability().namespace());
        mv.visitLdcInsn(h.capability().name());
        mv.visitTypeInsn(Opcodes.NEW, ARRAY_LIST);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ARRAY_LIST, "<init>", "()V", false);
        for (NexaIr.Value arg : h.args()) {
            mv.visitInsn(Opcodes.DUP);
            loadValue(mv, arg, slots);
            box(mv, arg.type());
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST, "add", "(Ljava/lang/Object;)Z", true);
            mv.visitInsn(Opcodes.POP);
        }
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, CTX, "callHostCapability", "(Ljava/lang/String;Ljava/lang/String;Ljava/util/List;)Ljava/lang/Object;", true);
        unbox(mv, h.result().type());
        storeResult(mv, h.result(), slots);
    }

    private void emitUnary(MethodVisitor mv, NexaIr.Unary u, SlotTable slots) {
        loadValue(mv, u.operand(), slots);
        switch (u.op()) {
            case "!" -> { emitBooleanNot(mv); }
            case "+" -> { }
            case "-" -> emitNegate(mv, u.result().type());
            default -> throw unsupported("Unsupported unary operator: " + u.op(), u);
        }
        storeResult(mv, u.result(), slots);
    }

    private void emitBinary(MethodVisitor mv, NexaIr.Binary b, SlotTable slots) {
        NexaType type = b.result().type();
        switch (b.op()) {
            case "&&" -> emitShortCircuitAnd(mv, b, slots);
            case "||" -> emitShortCircuitOr(mv, b, slots);
            case "+" -> {
                if (isString(type) || isString(b.left().type()) || isString(b.right().type())) {
                    emitStringConcat(mv, b, slots);
                } else {
                    loadValue(mv, b.left(), slots); convertNumeric(mv, b.left().type(), type);
                    loadValue(mv, b.right(), slots); convertNumeric(mv, b.right().type(), type);
                    emitArithmetic(mv, "+", type);
                }
            }
            case "-", "*", "/", "%" -> {
                loadValue(mv, b.left(), slots); convertNumeric(mv, b.left().type(), type);
                loadValue(mv, b.right(), slots); convertNumeric(mv, b.right().type(), type);
                emitArithmetic(mv, b.op(), type);
            }
            case "==", "!=", "<", "<=", ">", ">=" -> emitComparison(mv, b, slots);
            default -> throw unsupported("Unsupported binary operator: " + b.op(), b);
        }
        storeResult(mv, b.result(), slots);
    }

    private void emitComparison(MethodVisitor mv, NexaIr.Binary b, SlotTable slots) {
        NexaType leftType = b.left().type();
        if (isString(leftType) || isString(b.right().type())) {
            loadValue(mv, b.left(), slots);
            loadValue(mv, b.right(), slots);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
            if (!b.op().equals("==")) emitBooleanNot(mv);
            return;
        }
        loadValue(mv, b.left(), slots);
        convertNumeric(mv, leftType, b.right().type());
        loadValue(mv, b.right(), slots);
        convertNumeric(mv, b.right().type(), b.right().type());
        Label yes = new Label();
        Label done = new Label();
        int opcode = comparisonOpcode(b.op(), b.right().type());
        mv.visitJumpInsn(opcode, yes);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitJumpInsn(Opcodes.GOTO, done);
        mv.visitLabel(yes);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitLabel(done);
    }

    private int comparisonOpcode(String op, NexaType type) {
        boolean wide = isLong(type) || isDouble(type);
        if (isFloat(type)) {
            return switch (op) { case "==" -> Opcodes.IF_ICMPEQ; case "!=" -> Opcodes.IF_ICMPNE; case "<" -> Opcodes.IFLT; case "<=" -> Opcodes.IFLE; case ">" -> Opcodes.IFGT; case ">=" -> Opcodes.IFGE; default -> throw new IllegalArgumentException(op); };
        }
        if (wide) {
            // Caller emits LCMP/DCMP before this opcode.
            return switch (op) { case "==" -> Opcodes.IFEQ; case "!=" -> Opcodes.IFNE; case "<" -> Opcodes.IFLT; case "<=" -> Opcodes.IFLE; case ">" -> Opcodes.IFGT; case ">=" -> Opcodes.IFGE; default -> throw new IllegalArgumentException(op); };
        }
        return switch (op) { case "==" -> Opcodes.IF_ICMPEQ; case "!=" -> Opcodes.IF_ICMPNE; case "<" -> Opcodes.IF_ICMPLT; case "<=" -> Opcodes.IF_ICMPLE; case ">" -> Opcodes.IF_ICMPGT; case ">=" -> Opcodes.IF_ICMPGE; default -> throw new IllegalArgumentException(op); };
    }

    private void emitShortCircuitAnd(MethodVisitor mv, NexaIr.Binary b, SlotTable slots) {
        Label falseLabel = new Label();
        Label done = new Label();
        loadValue(mv, b.left(), slots);
        mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
        loadValue(mv, b.right(), slots);
        mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitJumpInsn(Opcodes.GOTO, done);
        mv.visitLabel(falseLabel);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitLabel(done);
    }

    private void emitShortCircuitOr(MethodVisitor mv, NexaIr.Binary b, SlotTable slots) {
        Label trueLabel = new Label();
        Label done = new Label();
        loadValue(mv, b.left(), slots);
        mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
        loadValue(mv, b.right(), slots);
        mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitJumpInsn(Opcodes.GOTO, done);
        mv.visitLabel(trueLabel);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitLabel(done);
    }

    private void emitStringConcat(MethodVisitor mv, NexaIr.Binary b, SlotTable slots) {
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        appendBuilder(mv, b.left(), slots);
        appendBuilder(mv, b.right(), slots);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
    }

    private void appendBuilder(MethodVisitor mv, NexaIr.Value value, SlotTable slots) {
        loadValue(mv, value, slots);
        String desc = descriptor(value.type());
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(" + desc + ")Ljava/lang/StringBuilder;", false);
    }

    private void emitArithmetic(MethodVisitor mv, String op, NexaType type) {
        if (isDouble(type)) { mv.visitInsn(switch (op) { case "+" -> Opcodes.DADD; case "-" -> Opcodes.DSUB; case "*" -> Opcodes.DMUL; case "/" -> Opcodes.DDIV; case "%" -> Opcodes.DREM; default -> throw new IllegalArgumentException(op); }); return; }
        if (isFloat(type)) { mv.visitInsn(switch (op) { case "+" -> Opcodes.FADD; case "-" -> Opcodes.FSUB; case "*" -> Opcodes.FMUL; case "/" -> Opcodes.FDIV; case "%" -> Opcodes.FREM; default -> throw new IllegalArgumentException(op); }); return; }
        if (isLong(type)) { mv.visitInsn(switch (op) { case "+" -> Opcodes.LADD; case "-" -> Opcodes.LSUB; case "*" -> Opcodes.LMUL; case "/" -> Opcodes.LDIV; case "%" -> Opcodes.LREM; default -> throw new IllegalArgumentException(op); }); return; }
        mv.visitInsn(switch (op) { case "+" -> Opcodes.IADD; case "-" -> Opcodes.ISUB; case "*" -> Opcodes.IMUL; case "/" -> Opcodes.IDIV; case "%" -> Opcodes.IREM; default -> throw new IllegalArgumentException(op); });
    }

    private void emitNegate(MethodVisitor mv, NexaType type) {
        if (isDouble(type)) mv.visitInsn(Opcodes.DNEG);
        else if (isFloat(type)) mv.visitInsn(Opcodes.FNEG);
        else if (isLong(type)) mv.visitInsn(Opcodes.LNEG);
        else mv.visitInsn(Opcodes.INEG);
    }

    private void emitBooleanNot(MethodVisitor mv) {
        Label yes = new Label();
        Label done = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, yes);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitJumpInsn(Opcodes.GOTO, done);
        mv.visitLabel(yes);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitLabel(done);
    }

    private void emitConst(MethodVisitor mv, Object value, NexaType type) {
        if (value == null) { mv.visitInsn(Opcodes.ACONST_NULL); return; }
        if (isBoolean(type)) mv.visitInsn(Boolean.TRUE.equals(value) ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        else if (value instanceof Integer || value instanceof Short || value instanceof Byte) mv.visitLdcInsn(((Number) value).intValue());
        else if (value instanceof Long) mv.visitLdcInsn(value);
        else if (value instanceof Float) mv.visitLdcInsn(value);
        else if (value instanceof Double) mv.visitLdcInsn(value);
        else if (value instanceof String) mv.visitLdcInsn(value);
        else throw new IllegalArgumentException("Unsupported constant: " + value.getClass());
    }

    private void loadValue(MethodVisitor mv, NexaIr.Value value, SlotTable slots) {
        mv.visitVarInsn(loadOpcode(value.type()), slots.valueSlot(value.id()));
    }

    private void storeResult(MethodVisitor mv, NexaIr.Value value, SlotTable slots) {
        mv.visitVarInsn(storeOpcode(value.type()), slots.valueSlot(value.id()));
    }

    private void loadLocal(MethodVisitor mv, String name, SlotTable slots) {
        NexaType type = slots.localTypes().get(name);
        if (type == null) throw new IllegalArgumentException("Unknown Nexa local: " + name);
        mv.visitVarInsn(loadOpcode(type), slots.localSlot(name));
    }

    private void storeLocal(MethodVisitor mv, String name, NexaType type, SlotTable slots) {
        mv.visitVarInsn(storeOpcode(type), slots.localSlot(name));
    }

    private void emitMapGet(MethodVisitor mv, String key) {
        mv.visitLdcInsn(key);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
    }

    private void box(MethodVisitor mv, NexaType type) {
        String n = primitiveName(type);
        switch (n) {
            case "BOOLEAN" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            case "INT8", "UINT8" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            case "INT16", "UINT16" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
            case "INT32", "UINT32" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            case "INT64", "UINT64" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            case "FLOAT32" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            case "FLOAT64" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            default -> { }
        }
    }

    private void unbox(MethodVisitor mv, NexaType type) {
        String n = primitiveName(type);
        switch (n) {
            case "BOOLEAN" -> { mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false); }
            case "INT8", "UINT8" -> { mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "byteValue", "()B", false); }
            case "INT16", "UINT16" -> { mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "shortValue", "()S", false); }
            case "INT32", "UINT32" -> { mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false); }
            case "INT64", "UINT64" -> { mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "longValue", "()J", false); }
            case "FLOAT32" -> { mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "floatValue", "()F", false); }
            case "FLOAT64" -> { mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number"); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false); }
            case "STRING" -> mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
            case "ARRAY" -> mv.visitTypeInsn(Opcodes.CHECKCAST, LIST);
            case "OBJECT" -> { }
            default -> { }
        }
    }

    private void convertNumeric(MethodVisitor mv, NexaType from, NexaType to) {
        if (!NexaType.numeric(from) || !NexaType.numeric(to) || NexaType.same(from, to)) return;
        if (isDouble(to)) {
            if (isLong(from)) mv.visitInsn(Opcodes.L2D); else if (isFloat(from)) mv.visitInsn(Opcodes.F2D); else mv.visitInsn(Opcodes.I2D);
        } else if (isFloat(to)) {
            if (isLong(from)) mv.visitInsn(Opcodes.L2F); else mv.visitInsn(Opcodes.I2F);
        } else if (isLong(to) && !isLong(from)) {
            mv.visitInsn(Opcodes.I2L);
        } else if (!isLong(to) && isLong(from)) {
            mv.visitInsn(Opcodes.L2I);
        } else if (!isLong(to) && !isFloat(to)) {
            // INT8/16/32 and UINT8/16/32 all use JVM int arithmetic.
        }
    }

    private void unboxIndex(MethodVisitor mv, NexaType type) {
        // List.get requires an int. The value is already primitive for scalar IR.
        convertNumeric(mv, type, NexaType.INT32);
    }

    private void unboxIndexFromObject(MethodVisitor mv, NexaType type, int objectSlot) {
        // Replace the scratch object on the stack with primitive int.
        mv.visitVarInsn(Opcodes.ALOAD, objectSlot);
        unbox(mv, NexaType.INT32);
    }

    private void emitDefault(MethodVisitor mv, NexaType type) {
        if (isReference(type)) mv.visitInsn(Opcodes.ACONST_NULL);
        else if (isLong(type)) mv.visitInsn(Opcodes.LCONST_0);
        else if (isDouble(type)) mv.visitInsn(Opcodes.DCONST_0);
        else if (isFloat(type)) mv.visitInsn(Opcodes.FCONST_0);
        else mv.visitInsn(Opcodes.ICONST_0);
    }

    private int loadOpcode(NexaType type) { return isReference(type) ? Opcodes.ALOAD : (isLong(type) ? Opcodes.LLOAD : (isDouble(type) ? Opcodes.DLOAD : Opcodes.ILOAD)); }
    private int storeOpcode(NexaType type) { return isReference(type) ? Opcodes.ASTORE : (isLong(type) ? Opcodes.LSTORE : (isDouble(type) ? Opcodes.DSTORE : Opcodes.ISTORE)); }
    private boolean isReference(NexaType type) { return !NexaType.numeric(type) && !isBoolean(type); }
    private boolean isBoolean(NexaType type) { return "BOOLEAN".equals(primitiveName(type)); }
    private boolean isLong(NexaType type) { return switch (primitiveName(type)) { case "INT64", "UINT64" -> true; default -> false; }; }
    private boolean isFloat(NexaType type) { return "FLOAT32".equals(primitiveName(type)); }
    private boolean isDouble(NexaType type) { return "FLOAT64".equals(primitiveName(type)); }
    private boolean isString(NexaType type) { return "STRING".equals(primitiveName(type)); }
    private boolean isArray(NexaType type) { return type instanceof NexaType.Array; }
    private String primitiveName(NexaType type) { return type instanceof NexaType.Primitive p ? p.displayName() : type.displayName(); }

    private String descriptor(NexaType type) {
        String n = primitiveName(type);
        return switch (n) {
            case "BOOLEAN" -> "Z";
            case "INT8", "UINT8" -> "B";
            case "INT16", "UINT16" -> "S";
            case "INT32", "UINT32" -> "I";
            case "INT64", "UINT64" -> "J";
            case "FLOAT32" -> "F";
            case "FLOAT64" -> "D";
            case "STRING" -> "Ljava/lang/String;";
            default -> "Ljava/lang/Object;";
        };
    }

    private String className(String programName, String functionName) {
        String raw = programName + "$" + functionName;
        StringBuilder sb = new StringBuilder("nexa/generated/");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }
        return sb.toString();
    }

    private IllegalArgumentException unsupported(String message, NexaIr.Instruction instruction) {
        return new IllegalArgumentException(message + " at " + instruction.span());
    }

    private static final class SlotTable {
        private final Map<Integer, Integer> values;
        private final Map<String, Integer> locals;
        private final Map<String, NexaType> localTypes;
        private int scratch;

        private SlotTable(Map<Integer, Integer> values, Map<String, Integer> locals, Map<String, NexaType> localTypes, int scratch) {
            this.values = values; this.locals = locals; this.localTypes = localTypes; this.scratch = scratch;
        }

        static SlotTable build(NexaIr.Function function) {
            Map<Integer, NexaType> types = new TreeMap<>();
            for (NexaIr.Block block : function.blocks()) {
                for (NexaIr.Instruction i : block.instructions()) {
                    if (i.result() != null) types.put(i.result().id(), i.result().type());
                }
            }
            Map<Integer, Integer> values = new HashMap<>();
            int slot = 5;
            for (Map.Entry<Integer, NexaType> e : types.entrySet()) {
                values.put(e.getKey(), slot);
                slot += wide(e.getValue()) ? 2 : 1;
            }
            Map<String, Integer> locals = new LinkedHashMap<>();
            Map<String, NexaType> localTypes = new LinkedHashMap<>();
            for (NexaIr.Local local : function.locals()) {
                if (!locals.containsKey(local.name())) {
                    locals.put(local.name(), slot);
                    localTypes.put(local.name(), local.type());
                    slot += wide(local.type()) ? 2 : 1;
                }
            }
            return new SlotTable(values, locals, localTypes, slot);
        }

        int valueSlot(int id) { return Objects.requireNonNull(values.get(id), "No JVM slot for value " + id); }
        int localSlot(String name) { return Objects.requireNonNull(locals.get(name), "No JVM slot for local " + name); }
        Map<String, NexaType> localTypes() { return localTypes; }
        int scratch1() { return scratch++; }
        int scratch2() { return scratch++; }
        int scratch3() { return scratch++; }
        int scratch4() { return scratch++; }
        int scratch5() { return scratch++; }
        static boolean wide(NexaType type) { return "INT64".equals(type.displayName()) || "UINT64".equals(type.displayName()) || "FLOAT64".equals(type.displayName()); }
    }
}
