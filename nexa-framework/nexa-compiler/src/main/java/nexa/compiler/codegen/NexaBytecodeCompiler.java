package nexa.compiler.codegen;

import nexa.compiler.ir.NexaIr;
import nexa.compiler.lang.NexaType;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.*;

/** AOT JVM backend for verified Nexa IR. */
public final class NexaBytecodeCompiler {
    private static final String NODE = "nexa/framework/runtime/api/NexaCompiledNode";
    private static final String MSG = "nexa/framework/runtime/api/model/RuntimeMessage";
    private static final String CTX = "nexa/framework/runtime/api/NexaExecutionContext";
    private static final String MAP = "java/util/Map";
    private static final String LIST = "java/util/List";
    private static final String ITERATOR = "java/util/Iterator";
    private static final String ARRAY_LIST = "java/util/ArrayList";
    private static final String LINKED_HASH_MAP = "java/util/LinkedHashMap";

    private final Map<String, Integer> tagSlots;

    public NexaBytecodeCompiler() { this(Map.of()); }
    public NexaBytecodeCompiler(Map<String, Integer> tagSlots) { this.tagSlots = Objects.requireNonNull(tagSlots); }

    public byte[] compile(NexaIr.Program program) { return compile(program, null); }

    public byte[] compile(NexaIr.Program program, String customClassName) {
        Objects.requireNonNull(program, "program");
        NexaIr.Function main = program.functions().stream().filter(f -> "main".equals(f.name())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Program does not contain main function"));
        return compileFunction(program, main, customClassName);
    }

    public byte[] compileFunction(NexaIr.Program program, NexaIr.Function function) { return compileFunction(program, function, null); }

    public byte[] compileFunction(NexaIr.Program program, NexaIr.Function function, String customClassName) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(function, "function");
        String name = customClassName != null ? customClassName.replace('.', '/') : className(program.name(), function.name());
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, "java/lang/Object", new String[]{NODE});
        emitConstructor(cw);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "execute", "(L" + MSG + ";L" + CTX + ";)V", null, null);
        mv.visitCode();
        Slots slots = Slots.build(function);
        emitAliases(mv);
        Map<Integer, Label> labels = new HashMap<>();
        for (NexaIr.Block b : function.blocks()) labels.put(b.id(), new Label());
        for (NexaIr.Block b : function.blocks()) {
            mv.visitLabel(labels.get(b.id()));
            for (NexaIr.Instruction i : b.instructions()) emit(mv, i, slots);
            terminate(mv, b.terminator(), labels, slots);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private void emitConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode(); mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(1, 1); mv.visitEnd();
    }

    private void emitAliases(MethodVisitor mv) {
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MSG, "values", "()Ljava/util/concurrent/ConcurrentMap;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MSG, "values", "()Ljava/util/concurrent/ConcurrentMap;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 4);
    }

    private void emit(MethodVisitor mv, NexaIr.Instruction i, Slots s) {
        if (i instanceof NexaIr.Const x) {
            constant(mv, x.value(), x.result().type()); store(mv, x.result(), s);
        } else if (i instanceof NexaIr.LoadLocal x) {
            loadLocal(mv, x.name(), s); store(mv, x.result(), s);
        } else if (i instanceof NexaIr.LoadTag x) {
            loadTag(mv, x, s);
        } else if (i instanceof NexaIr.StoreLocal x) {
            load(mv, x.value(), s); convert(mv, x.value().type(), s.localType(x.name())); storeLocal(mv, x.name(), s);
        } else if (i instanceof NexaIr.StoreTag x) {
            storeTag(mv, x, s);
        } else if (i instanceof NexaIr.LoadField x) {
            if (s.isMessageValue(x.target())) {
                loadMessage(mv);
                mv.visitLdcInsn(x.field());
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MSG, "readRawValue", "(Ljava/lang/String;)Ljava/lang/Object;", false);
            } else {
                load(mv, x.target(), s); mv.visitLdcInsn(x.field());
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            }
            unbox(mv, x.result().type()); store(mv, x.result(), s);
        } else if (i instanceof NexaIr.StoreField x) {
            if (s.isMessageValue(x.target())) {
                loadMessage(mv);
                mv.visitLdcInsn(x.field());
                load(mv, x.value(), s); box(mv, x.value().type());
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MSG, "writeValue", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
            } else {
                int map = s.scratch(), value = s.scratch();
                load(mv, x.target(), s); mv.visitVarInsn(Opcodes.ASTORE, map);
                load(mv, x.value(), s); box(mv, x.value().type()); mv.visitVarInsn(Opcodes.ASTORE, value);
                mv.visitVarInsn(Opcodes.ALOAD, map); mv.visitLdcInsn(x.field()); mv.visitVarInsn(Opcodes.ALOAD, value);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
                mv.visitInsn(Opcodes.POP);
            }
        } else if (i instanceof NexaIr.LoadIndex x) {
            load(mv, x.target(), s); load(mv, x.index(), s);
            if (isArray(x.target().type())) {
                convert(mv, x.index().type(), NexaType.INT32);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST, "get", "(I)Ljava/lang/Object;", true);
            } else {
                box(mv, x.index().type());
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            }
            unbox(mv, x.result().type()); store(mv, x.result(), s);
        } else if (i instanceof NexaIr.StoreIndex x) {
            int target = s.scratch(), key = s.scratch(), value = s.scratch();
            load(mv, x.target(), s); mv.visitVarInsn(Opcodes.ASTORE, target);
            load(mv, x.index(), s); box(mv, x.index().type()); mv.visitVarInsn(Opcodes.ASTORE, key);
            load(mv, x.value(), s); box(mv, x.value().type()); mv.visitVarInsn(Opcodes.ASTORE, value);
            mv.visitVarInsn(Opcodes.ALOAD, target);
            if (isArray(x.target().type())) {
                mv.visitVarInsn(Opcodes.ALOAD, key); unbox(mv, NexaType.INT32); mv.visitVarInsn(Opcodes.ALOAD, value);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST, "set", "(ILjava/lang/Object;)Ljava/lang/Object;", true);
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, key); mv.visitVarInsn(Opcodes.ALOAD, value);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
            }
            mv.visitInsn(Opcodes.POP);
        } else if (i instanceof NexaIr.ArrayCreate x) {
            mv.visitTypeInsn(Opcodes.NEW, ARRAY_LIST); mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ARRAY_LIST, "<init>", "()V", false);
            for (NexaIr.Value v : x.values()) {
                mv.visitInsn(Opcodes.DUP); load(mv, v, s); box(mv, v.type());
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST, "add", "(Ljava/lang/Object;)Z", true); mv.visitInsn(Opcodes.POP);
            }
            store(mv, x.result(), s);
        } else if (i instanceof NexaIr.ObjectCreate x) {
            mv.visitTypeInsn(Opcodes.NEW, LINKED_HASH_MAP); mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, LINKED_HASH_MAP, "<init>", "()V", false);
            for (var e : x.fields().entrySet()) {
                mv.visitInsn(Opcodes.DUP); mv.visitLdcInsn(e.getKey()); load(mv, e.getValue(), s); box(mv, e.getValue().type());
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true); mv.visitInsn(Opcodes.POP);
            }
            store(mv, x.result(), s);
        } else if (i instanceof NexaIr.Unary x) {
            load(mv, x.operand(), s);
            switch (x.op()) { case "!" -> not(mv); case "+" -> {} case "-" -> negate(mv, x.result().type()); default -> unsupported(x.op(), x); }
            store(mv, x.result(), s);
        } else if (i instanceof NexaIr.Binary x) {
            binary(mv, x, s); store(mv, x.result(), s);
        } else if (i instanceof NexaIr.Call x) {
            call(mv, x, s);
        } else if (i instanceof NexaIr.HostCall x) {
            hostCall(mv, x, s);
        } else if (i instanceof NexaIr.Iterate x) {
            load(mv, x.iterable(), s); mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true); store(mv, x.result(), s);
        } else if (i instanceof NexaIr.IterHasNext x) {
            load(mv, x.iterator(), s); mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, ITERATOR, "hasNext", "()Z", true); store(mv, x.result(), s);
        } else if (i instanceof NexaIr.IterNext x) {
            load(mv, x.iterator(), s); mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, ITERATOR, "next", "()Ljava/lang/Object;", true); unbox(mv, x.result().type()); store(mv, x.result(), s);
        } else if (i instanceof NexaIr.Return x) {
            if (x.value() != null) { load(mv, x.value(), s); if (x.value().type() != NexaType.VOID) mv.visitInsn(Opcodes.POP); }
            mv.visitInsn(Opcodes.RETURN);
        } else throw new IllegalArgumentException("Unsupported Nexa IR instruction: " + i.getClass().getName());
    }

    private void terminate(MethodVisitor mv, NexaIr.Terminator t, Map<Integer, Label> labels, Slots s) {
        if (t instanceof NexaIr.Jump x) mv.visitJumpInsn(Opcodes.GOTO, labels.get(x.targetBlock()));
        else if (t instanceof NexaIr.Branch x) { load(mv, x.condition(), s); mv.visitJumpInsn(Opcodes.IFNE, labels.get(x.trueBlock())); mv.visitJumpInsn(Opcodes.GOTO, labels.get(x.falseBlock())); }
        else if (t instanceof NexaIr.Stop) mv.visitInsn(Opcodes.RETURN);
        else throw new IllegalArgumentException("Unsupported terminator: " + t.getClass().getName());
    }

    private void call(MethodVisitor mv, NexaIr.Call x, Slots s) {
        if (!"send".equals(x.target())) throw unsupported(x.target(), x);
        if (x.args().size() == 1) {
            if (s.isMessageValue(x.args().getFirst())) loadMessage(mv); else load(mv, x.args().getFirst(), s);
            int arg = s.scratch(); mv.visitVarInsn(Opcodes.ASTORE, arg);
            mv.visitVarInsn(Opcodes.ALOAD, 2); mv.visitVarInsn(Opcodes.ALOAD, arg);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, CTX, "send", "(L" + MSG + ";)V", true);
        } else if (x.args().size() == 2) {
            NexaIr.Value first = x.args().get(0), second = x.args().get(1);
            if (s.isMessageValue(first)) loadMessage(mv); else load(mv, first, s);
            int a = s.scratch(); mv.visitVarInsn(Opcodes.ASTORE, a);
            if (s.isMessageValue(second)) loadMessage(mv); else load(mv, second, s);
            int b = s.scratch(); mv.visitVarInsn(Opcodes.ASTORE, b);
            mv.visitVarInsn(Opcodes.ALOAD, 2); mv.visitVarInsn(Opcodes.ALOAD, a); mv.visitVarInsn(Opcodes.ALOAD, b);
            String desc = isString(first.type()) ? "(Ljava/lang/String;L" + MSG + ";)V" : "(Ljava/util/List;L" + MSG + ";)V";
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, CTX, "send", desc, true);
        } else throw unsupported("send expects 1 or 2 arguments", x);
        if (x.result() != null) { defaultValue(mv, x.result().type()); store(mv, x.result(), s); }
    }

    private void hostCall(MethodVisitor mv, NexaIr.HostCall x, Slots s) {
        mv.visitVarInsn(Opcodes.ALOAD, 2); mv.visitLdcInsn(x.capability().namespace()); mv.visitLdcInsn(x.capability().name());
        mv.visitTypeInsn(Opcodes.NEW, ARRAY_LIST); mv.visitInsn(Opcodes.DUP); mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ARRAY_LIST, "<init>", "()V", false);
        for (NexaIr.Value v : x.args()) { mv.visitInsn(Opcodes.DUP); load(mv, v, s); box(mv, v.type()); mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST, "add", "(Ljava/lang/Object;)Z", true); mv.visitInsn(Opcodes.POP); }
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, CTX, "callHostCapability", "(Ljava/lang/String;Ljava/lang/String;Ljava/util/List;)Ljava/lang/Object;", true);
        unbox(mv, x.result().type()); store(mv, x.result(), s);
    }

    private void binary(MethodVisitor mv, NexaIr.Binary x, Slots s) {
        switch (x.op()) {
            case "&&" -> logicalAnd(mv, x, s); case "||" -> logicalOr(mv, x, s);
            case "+" -> { if (isString(x.result().type()) || isString(x.left().type()) || isString(x.right().type())) concat(mv, x, s); else arithmetic(mv, x, s, "+"); }
            case "-", "*", "/", "%" -> arithmetic(mv, x, s, x.op());
            case "==", "!=", "<", "<=", ">", ">=" -> compare(mv, x, s);
            default -> throw unsupported(x.op(), x);
        }
    }

    private void arithmetic(MethodVisitor mv, NexaIr.Binary x, Slots s, String op) {
        load(mv, x.left(), s); convert(mv, x.left().type(), x.result().type()); load(mv, x.right(), s); convert(mv, x.right().type(), x.result().type());
        int insn = switch (x.result().type().displayName()) {
            case "INT64", "UINT64" -> switch (op) { case "+" -> Opcodes.LADD; case "-" -> Opcodes.LSUB; case "*" -> Opcodes.LMUL; case "/" -> Opcodes.LDIV; default -> Opcodes.LREM; };
            case "FLOAT32" -> switch (op) { case "+" -> Opcodes.FADD; case "-" -> Opcodes.FSUB; case "*" -> Opcodes.FMUL; case "/" -> Opcodes.FDIV; default -> Opcodes.FREM; };
            case "FLOAT64" -> switch (op) { case "+" -> Opcodes.DADD; case "-" -> Opcodes.DSUB; case "*" -> Opcodes.DMUL; case "/" -> Opcodes.DDIV; default -> Opcodes.DREM; };
            default -> switch (op) { case "+" -> Opcodes.IADD; case "-" -> Opcodes.ISUB; case "*" -> Opcodes.IMUL; case "/" -> Opcodes.IDIV; default -> Opcodes.IREM; };
        };
        mv.visitInsn(insn);
    }

    private void compare(MethodVisitor mv, NexaIr.Binary x, Slots s) {
        if (isString(x.left().type()) || isString(x.right().type())) {
            if (!"==".equals(x.op()) && !"!=".equals(x.op())) throw unsupported("Only == and != are supported for strings", x);
            load(mv, x.left(), s); load(mv, x.right(), s); mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
            if ("!=".equals(x.op())) not(mv); return;
        }
        NexaType common = x.left().type(); load(mv, x.left(), s); convert(mv, x.left().type(), common); load(mv, x.right(), s); convert(mv, x.right().type(), common);
        if (isLong(common)) mv.visitInsn(Opcodes.LCMP); else if (isDouble(common)) mv.visitInsn(Opcodes.DCMPL); else if (isFloat(common)) mv.visitInsn(Opcodes.FCMPL);
        Label yes = new Label(), done = new Label();
        int op = switch (x.op()) { case "==" -> Opcodes.IFEQ; case "!=" -> Opcodes.IFNE; case "<" -> Opcodes.IFLT; case "<=" -> Opcodes.IFLE; case ">" -> Opcodes.IFGT; case ">=" -> Opcodes.IFGE; default -> throw new IllegalArgumentException(x.op()); };
        if (!isLong(common) && !isDouble(common) && !isFloat(common)) mv.visitJumpInsn(switch (x.op()) { case "==" -> Opcodes.IF_ICMPEQ; case "!=" -> Opcodes.IF_ICMPNE; case "<" -> Opcodes.IF_ICMPLT; case "<=" -> Opcodes.IF_ICMPLE; case ">" -> Opcodes.IF_ICMPGT; default -> Opcodes.IF_ICMPGE; }, yes); else mv.visitJumpInsn(op, yes);
        mv.visitInsn(Opcodes.ICONST_0); mv.visitJumpInsn(Opcodes.GOTO, done); mv.visitLabel(yes); mv.visitInsn(Opcodes.ICONST_1); mv.visitLabel(done);
    }

    private void logicalAnd(MethodVisitor mv, NexaIr.Binary x, Slots s) { Label f = new Label(), d = new Label(); load(mv,x.left(),s); mv.visitJumpInsn(Opcodes.IFEQ,f); load(mv,x.right(),s); mv.visitJumpInsn(Opcodes.IFEQ,f); mv.visitInsn(Opcodes.ICONST_1); mv.visitJumpInsn(Opcodes.GOTO,d); mv.visitLabel(f); mv.visitInsn(Opcodes.ICONST_0); mv.visitLabel(d); }
    private void logicalOr(MethodVisitor mv, NexaIr.Binary x, Slots s) { Label t = new Label(), d = new Label(); load(mv,x.left(),s); mv.visitJumpInsn(Opcodes.IFNE,t); load(mv,x.right(),s); mv.visitJumpInsn(Opcodes.IFNE,t); mv.visitInsn(Opcodes.ICONST_0); mv.visitJumpInsn(Opcodes.GOTO,d); mv.visitLabel(t); mv.visitInsn(Opcodes.ICONST_1); mv.visitLabel(d); }

    private void concat(MethodVisitor mv, NexaIr.Binary x, Slots s) {
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder"); mv.visitInsn(Opcodes.DUP); mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        append(mv,x.left(),s); append(mv,x.right(),s); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
    }
    private void append(MethodVisitor mv, NexaIr.Value v, Slots s) { load(mv,v,s); String d=switch(v.type().displayName()){case "BOOLEAN"->"(Z)";case "INT8","UINT8","INT16","UINT16","INT32","UINT32"->"(I)";case "INT64","UINT64"->"(J)";case "FLOAT32"->"(F)";case "FLOAT64"->"(D)";case "STRING"->"(Ljava/lang/String;)";default->"(Ljava/lang/Object;)";}; mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append",d+"Ljava/lang/StringBuilder;",false); }

    private void constant(MethodVisitor mv, Object value, NexaType type) { if(value==null){mv.visitInsn(Opcodes.ACONST_NULL);return;} if(isBoolean(type))mv.visitInsn(Boolean.TRUE.equals(value)?Opcodes.ICONST_1:Opcodes.ICONST_0); else if(value instanceof Number n&&!isLong(type)&&!isFloat(type)&&!isDouble(type))mv.visitLdcInsn(n.intValue()); else mv.visitLdcInsn(value); }
    private void load(MethodVisitor mv, NexaIr.Value v, Slots s) { mv.visitVarInsn(loadOp(v.type()), s.value(v.id())); }
    private void store(MethodVisitor mv, NexaIr.Value v, Slots s) { mv.visitVarInsn(storeOp(v.type()), s.value(v.id())); }
    private void loadMessage(MethodVisitor mv) { mv.visitVarInsn(Opcodes.ALOAD, 1); }
    private void loadLocal(MethodVisitor mv, String n, Slots s) { if("msg".equals(n)||"message".equals(n))mv.visitVarInsn(Opcodes.ALOAD,1); else mv.visitVarInsn(loadOp(s.localType(n)),s.local(n)); }
    private void storeLocal(MethodVisitor mv, String n, Slots s) { if("msg".equals(n)||"message".equals(n))mv.visitVarInsn(Opcodes.ASTORE,1); else mv.visitVarInsn(storeOp(s.localType(n)),s.local(n)); }

    private void box(MethodVisitor mv, NexaType type) { switch(type.displayName()){case "BOOLEAN"->mv.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Boolean","valueOf","(Z)Ljava/lang/Boolean;",false);case "INT8","UINT8"->mv.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Byte","valueOf","(B)Ljava/lang/Byte;",false);case "INT16","UINT16"->mv.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Short","valueOf","(S)Ljava/lang/Short;",false);case "INT32","UINT32"->mv.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false);case "INT64","UINT64"->mv.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Long","valueOf","(J)Ljava/lang/Long;",false);case "FLOAT32"->mv.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Float","valueOf","(F)Ljava/lang/Float;",false);case "FLOAT64"->mv.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Double","valueOf","(D)Ljava/lang/Double;",false);default->{}} }
    private void unbox(MethodVisitor mv, NexaType type) { switch(type.displayName()){case "BOOLEAN"->{mv.visitTypeInsn(Opcodes.CHECKCAST,"java/lang/Boolean");mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Boolean","booleanValue","()Z",false);}case "INT8","UINT8"->{mv.visitTypeInsn(Opcodes.CHECKCAST,"java/lang/Number");mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Number","byteValue","()B",false);}case "INT16","UINT16"->{mv.visitTypeInsn(Opcodes.CHECKCAST,"java/lang/Number");mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Number","shortValue","()S",false);}case "INT32","UINT32"->{mv.visitTypeInsn(Opcodes.CHECKCAST,"java/lang/Number");mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Number","intValue","()I",false);}case "INT64","UINT64"->{mv.visitTypeInsn(Opcodes.CHECKCAST,"java/lang/Number");mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Number","longValue","()J",false);}case "FLOAT32"->{mv.visitTypeInsn(Opcodes.CHECKCAST,"java/lang/Number");mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Number","floatValue","()F",false);}case "FLOAT64"->{mv.visitTypeInsn(Opcodes.CHECKCAST,"java/lang/Number");mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Number","doubleValue","()D",false);}case "STRING"->mv.visitTypeInsn(Opcodes.CHECKCAST,"java/lang/String");default->{}} }
    private void convert(MethodVisitor mv,NexaType from,NexaType to){if(!NexaType.numeric(from)||!NexaType.numeric(to)||from.displayName().equals(to.displayName()))return;if(isDouble(to)){if(isLong(from))mv.visitInsn(Opcodes.L2D);else if(isFloat(from))mv.visitInsn(Opcodes.F2D);else mv.visitInsn(Opcodes.I2D);}else if(isFloat(to)){if(isLong(from))mv.visitInsn(Opcodes.L2F);else mv.visitInsn(Opcodes.I2F);}else if(isLong(to)&&!isLong(from))mv.visitInsn(Opcodes.I2L);else if(!isLong(to)&&isLong(from))mv.visitInsn(Opcodes.L2I);}
    private void negate(MethodVisitor mv,NexaType t){if(isDouble(t))mv.visitInsn(Opcodes.DNEG);else if(isFloat(t))mv.visitInsn(Opcodes.FNEG);else if(isLong(t))mv.visitInsn(Opcodes.LNEG);else mv.visitInsn(Opcodes.INEG);}
    private void not(MethodVisitor mv){Label one=new Label(),done=new Label();mv.visitJumpInsn(Opcodes.IFEQ,one);mv.visitInsn(Opcodes.ICONST_0);mv.visitJumpInsn(Opcodes.GOTO,done);mv.visitLabel(one);mv.visitInsn(Opcodes.ICONST_1);mv.visitLabel(done);}
    private void defaultValue(MethodVisitor mv,NexaType t){if(isReference(t))mv.visitInsn(Opcodes.ACONST_NULL);else if(isLong(t))mv.visitInsn(Opcodes.LCONST_0);else if(isDouble(t))mv.visitInsn(Opcodes.DCONST_0);else if(isFloat(t))mv.visitInsn(Opcodes.FCONST_0);else mv.visitInsn(Opcodes.ICONST_0);}
    private int loadOp(NexaType t){return isReference(t)?Opcodes.ALOAD:isLong(t)?Opcodes.LLOAD:isDouble(t)?Opcodes.DLOAD:Opcodes.ILOAD;}
    private int storeOp(NexaType t){return isReference(t)?Opcodes.ASTORE:isLong(t)?Opcodes.LSTORE:isDouble(t)?Opcodes.DSTORE:Opcodes.ISTORE;}
    private boolean isReference(NexaType t){return !NexaType.numeric(t)&&!isBoolean(t);} private boolean isBoolean(NexaType t){return "BOOLEAN".equals(t.displayName());}
    private boolean isLong(NexaType t){return "INT64".equals(t.displayName())||"UINT64".equals(t.displayName());} private boolean isFloat(NexaType t){return "FLOAT32".equals(t.displayName());}
    private boolean isDouble(NexaType t){return "FLOAT64".equals(t.displayName());} private boolean isString(NexaType t){return "STRING".equals(t.displayName());}
    private boolean isArray(NexaType t){return t instanceof NexaType.Array;}
    private boolean isIntLike(NexaType t){String n=t.displayName();return "INT8".equals(n)||"UINT8".equals(n)||"INT16".equals(n)||"UINT16".equals(n)||"INT32".equals(n)||"UINT32".equals(n);}

    private void loadTag(MethodVisitor mv,NexaIr.LoadTag x,Slots s){int slotIndex=tagSlots.getOrDefault(x.name(),0);mv.visitVarInsn(Opcodes.ALOAD,2);mv.visitLdcInsn(slotIndex);NexaType type=x.result().type();if(isBoolean(type)||isIntLike(type)){mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,CTX,"readTagInt","(I)I",true);store(mv,x.result(),s);}else if(isLong(type)){mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,CTX,"readTagLong","(I)J",true);store(mv,x.result(),s);}else if(isFloat(type)||isDouble(type)){mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,CTX,"readTagDouble","(I)D",true);if(isFloat(type))mv.visitInsn(Opcodes.D2F);store(mv,x.result(),s);}else{mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,CTX,"readTagObject","(I)Ljava/lang/Object;",true);unbox(mv,type);store(mv,x.result(),s);}}
    private void storeTag(MethodVisitor mv,NexaIr.StoreTag x,Slots s){int slotIndex=tagSlots.getOrDefault(x.name(),0);mv.visitVarInsn(Opcodes.ALOAD,2);mv.visitLdcInsn(slotIndex);load(mv,x.value(),s);NexaType type=x.value().type();if(isBoolean(type)||isIntLike(type)){convert(mv,type,NexaType.INT32);mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,CTX,"writeTagInt","(II)V",true);}else if(isLong(type)){mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,CTX,"writeTagLong","(IJ)V",true);}else if(isFloat(type)||isDouble(type)){if(isFloat(type))mv.visitInsn(Opcodes.F2D);mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,CTX,"writeTagDouble","(ID)V",true);}else{box(mv,type);mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,CTX,"writeTagObject","(ILjava/lang/Object;)V",true);}}
    private String className(String program,String function){String raw="nexa/generated/"+program+"$"+function;StringBuilder out=new StringBuilder();for(int i=0;i<raw.length();i++)out.append(Character.isJavaIdentifierPart(raw.charAt(i))||raw.charAt(i)=='/'?raw.charAt(i):'_');return out.toString();}
    private IllegalArgumentException unsupported(String what,NexaIr.Instruction i){return new IllegalArgumentException("Unsupported Nexa bytecode operation '"+what+"' at "+i.span());}

    private static final class Slots {
        private final Map<Integer,Integer> values; private final Map<String,Integer> locals; private final Map<String,NexaType> types; private final Set<Integer> messageValues; private int nextScratch;
        private Slots(Map<Integer,Integer> values,Map<String,Integer> locals,Map<String,NexaType> types,Set<Integer> messageValues,int nextScratch){this.values=values;this.locals=locals;this.types=types;this.messageValues=messageValues;this.nextScratch=nextScratch;}
        static Slots build(NexaIr.Function f){
            Map<Integer,NexaType> resultTypes=new TreeMap<>(); Set<Integer> messageValues=new HashSet<>();
            for(NexaIr.Block b:f.blocks()) for(NexaIr.Instruction i:b.instructions()) { if(i.result()!=null) resultTypes.put(i.result().id(),i.result().type()); if(i instanceof NexaIr.LoadLocal l && ("msg".equals(l.name())||"message".equals(l.name()))) messageValues.add(l.result().id()); }
            Map<Integer,Integer> values=new HashMap<>();int slot=5;for(var e:resultTypes.entrySet()){values.put(e.getKey(),slot);slot+=wide(e.getValue())?2:1;}
            Map<String,Integer> locals=new LinkedHashMap<>();Map<String,NexaType> types=new LinkedHashMap<>();for(NexaIr.Local l:f.locals())if(!locals.containsKey(l.name())){locals.put(l.name(),slot);types.put(l.name(),l.type());slot+=wide(l.type())?2:1;}
            return new Slots(values,locals,types,messageValues,slot);
        }
        int value(int id){return Objects.requireNonNull(values.get(id),"No JVM slot for value "+id);} int local(String n){return Objects.requireNonNull(locals.get(n),"No JVM slot for local "+n);}
        NexaType localType(String n){if("msg".equals(n)||"message".equals(n))return NexaType.OBJECT;return Objects.requireNonNull(types.get(n),"No type for local "+n);} boolean isMessageValue(NexaIr.Value v){return messageValues.contains(v.id());}
        int scratch(){return nextScratch++;} static boolean wide(NexaType t){return "INT64".equals(t.displayName())||"UINT64".equals(t.displayName())||"FLOAT64".equals(t.displayName());}
    }
}
