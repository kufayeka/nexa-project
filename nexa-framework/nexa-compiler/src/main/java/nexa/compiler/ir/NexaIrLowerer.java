package nexa.compiler.ir;

import java.util.*;

import nexa.compiler.lang.NexaAst;
import nexa.compiler.lang.NexaAst.*;
import nexa.compiler.lang.NexaType;
import nexa.compiler.lang.SourceSpan;

/**
 * Lowers the current typed AST into a backend-neutral Nexa IR.
 *
 * Plugin/host calls are represented symbolically as HostCall. The compiler
 * never links against a plugin implementation.
 */
public final class NexaIrLowerer {
    private static final int IR_VERSION = 1;

    private final Map<String, NexaType> types = new LinkedHashMap<>();
    private final Map<String, NexaIr.Local> locals = new LinkedHashMap<>();
    private final List<NexaIr.Instruction> instructions = new ArrayList<>();
    private final List<NexaIr.Local> localList = new ArrayList<>();
    private final List<NexaIr.Block> blocks = new ArrayList<>();
    private int nextValue;
    private int nextBlock;
    private int currentBlock;
    private boolean terminated;

    public NexaIr.Program lower(NexaAst.Program program) {
        Objects.requireNonNull(program, "program");
        reset();
        registerBuiltins();

        switchTo(allocateBlock());
        for (Stmt stmt : program.statements()) statement(stmt);

        finishCurrentBlock(program.statements().isEmpty()
                ? new SourceSpan(0, 0)
                : program.statements().get(program.statements().size() - 1).span());

        NexaIr.Function main = new NexaIr.Function(
                "main", localList, blocks, NexaType.VOID, List.of());

        return new NexaIr.Program("nexa.program", List.of(main), types, IR_VERSION);
    }

    private void registerBuiltins() {
        for (NexaType t : List.of(
                NexaType.BOOLEAN, NexaType.INT8, NexaType.INT16,
                NexaType.INT32, NexaType.INT64, NexaType.UINT8,
                NexaType.UINT16, NexaType.UINT32, NexaType.UINT64,
                NexaType.FLOAT32, NexaType.FLOAT64, NexaType.STRING,
                NexaType.OBJECT, NexaType.VOID)) {
            types.put(t.displayName(), t);
        }
    }

    private void reset() {
        types.clear();
        locals.clear();
        localList.clear();
        instructions.clear();
        blocks.clear();
        nextValue = 0;
        nextBlock = 0;
        currentBlock = -1;
        terminated = false;
    }

    private void statement(Stmt stmt) {
        if (terminated) return;

        if (stmt instanceof TypeDecl typeDecl) {
            types.put(typeDecl.name(), resolve(typeDecl.type()));
            return;
        }

        if (stmt instanceof Let let) {
            NexaType type = resolve(let.type());
            NexaIr.Local local = new NexaIr.Local(let.name(), type, let.constant());
            locals.put(let.name(), local);
            localList.add(local);

            // Preserve the frontend's contextual type information in IR.
            // In particular, integer literals default to INT64, but a typed
            // declaration such as ARRAY<INT32> supplies the required element
            // type to its literal initializer. No runtime cast is needed for
            // a compile-time constant that is already known to fit.
            NexaIr.Value value = expression(let.init(), type);
            emit(new NexaIr.StoreLocal(null, let.name(), value, let.constant(), let.span()));
            return;
        }

        if (stmt instanceof Assign assign) {
            NexaIr.Value value = expression(assign.value());
            store(assign.target(), value, assign.span());
            return;
        }

        if (stmt instanceof Return ret) {
            NexaIr.Value value = ret.value() == null ? null : expression(ret.value());
            emit(new NexaIr.Return(null, value, ret.span()));
            terminate(new NexaIr.Stop(ret.span()));
            return;
        }

        if (stmt instanceof ExprStmt exprStmt) {
            expression(exprStmt.expr());
            return;
        }

        if (stmt instanceof For loop) lowerFor(loop);
    }

    private void lowerFor(For loop) {
        NexaIr.Value iterable = expression(loop.iterable());
        NexaIr.Value iterator = value(NexaType.OBJECT);
        emit(new NexaIr.Iterate(iterator, iterable, loop.iterable().span()));

        int header = allocateBlock();
        int body = allocateBlock();
        int exit = allocateBlock();

        terminate(new NexaIr.Jump(header, loop.span()));
        switchTo(header);

        NexaIr.Value hasNext = value(NexaType.BOOLEAN);
        emit(new NexaIr.IterHasNext(hasNext, iterator, loop.span()));
        terminate(new NexaIr.Branch(hasNext, body, exit, loop.span()));

        switchTo(body);
        NexaType elementType = resolve(loop.declaredType());
        NexaIr.Value element = value(elementType);
        emit(new NexaIr.IterNext(element, iterator, elementType, loop.span()));

        NexaIr.Local previous = locals.get(loop.name());
        NexaIr.Local loopLocal = new NexaIr.Local(loop.name(), elementType, false);
        locals.put(loop.name(), loopLocal);
        if (previous == null) localList.add(loopLocal);
        emit(new NexaIr.StoreLocal(null, loop.name(), element, false, loop.span()));

        for (Stmt stmt : loop.body()) statement(stmt);
        if (!terminated) terminate(new NexaIr.Jump(header, loop.span()));

        if (previous == null) locals.remove(loop.name());
        else locals.put(loop.name(), previous);

        switchTo(exit);
    }

    private NexaIr.Value expression(Expr expr) {
        return expression(expr, null);
    }

    /** Lowers an expression with an optional contextual expected type. */
    private NexaIr.Value expression(Expr expr, NexaType expectedType) {
        if (expr instanceof Literal literal) {
            NexaType literalType = resolve(literal.type());
            NexaType type = contextualLiteralType(literal, literalType, expectedType);
            NexaIr.Value result = value(type);
            emit(new NexaIr.Const(result, literal.value(), literal.span()));
            return result;
        }

        if (expr instanceof Var variable) {
            NexaIr.Local local = locals.get(variable.name());
            NexaType type = local == null ? NexaType.OBJECT : local.type();
            NexaIr.Value result = value(type);
            emit(new NexaIr.LoadLocal(result, variable.name(), variable.span()));
            return result;
        }

        if (expr instanceof Field field) {
            NexaIr.Value target = expression(field.target());
            NexaType type = fieldType(target.type(), field.name());
            NexaIr.Value result = value(type);
            emit(new NexaIr.LoadField(result, target, field.name(), field.span()));
            return result;
        }

        if (expr instanceof Index index) {
            NexaIr.Value target = expression(index.target());
            NexaIr.Value key = expression(index.index());
            NexaType type = indexType(target.type());
            NexaIr.Value result = value(type);
            emit(new NexaIr.LoadIndex(result, target, key, index.span()));
            return result;
        }

        if (expr instanceof Array array) {
            NexaType expectedElement = expectedArrayElement(expectedType);
            List<NexaIr.Value> values = new ArrayList<>();
            NexaType elementType = expectedElement == null ? NexaType.OBJECT : expectedElement;

            for (Expr item : array.values()) {
                NexaIr.Value itemValue = expression(item, expectedElement);
                values.add(itemValue);
                if (expectedElement == null) {
                    if (NexaType.same(elementType, NexaType.OBJECT)) {
                        elementType = itemValue.type();
                    } else {
                        elementType = common(elementType, itemValue.type());
                    }
                }
            }

            if (expectedElement == null && values.isEmpty()) {
                elementType = NexaType.OBJECT;
            }

            NexaIr.Value result = value(new NexaType.Array(elementType));
            emit(new NexaIr.ArrayCreate(result, values, elementType, array.span()));
            return result;
        }

        if (expr instanceof ObjectLit objectLit) {
            Map<String, NexaIr.Value> fields = new LinkedHashMap<>();
            Map<String, NexaType> fieldTypes = new LinkedHashMap<>();
            NexaType.ObjectType expectedObject = expectedType instanceof NexaType.ObjectType o ? o : null;

            for (var entry : objectLit.fields().entrySet()) {
                NexaType fieldExpected = expectedObject == null
                        ? null
                        : expectedObject.fields().get(entry.getKey());
                NexaIr.Value fieldValue = expression(entry.getValue(), fieldExpected);
                fields.put(entry.getKey(), fieldValue);
                fieldTypes.put(entry.getKey(), fieldValue.type());
            }
            NexaType.ObjectType objectType = new NexaType.ObjectType(fieldTypes);
            NexaIr.Value result = value(objectType);
            emit(new NexaIr.ObjectCreate(result, fields, objectType, objectLit.span()));
            return result;
        }

        if (expr instanceof Unary unary) {
            NexaIr.Value operand = expression(unary.expr());
            NexaType resultType = unary.op().equals("!") ? NexaType.BOOLEAN : operand.type();
            NexaIr.Value result = value(resultType);
            emit(new NexaIr.Unary(result, unary.op(), operand, unary.span()));
            return result;
        }

        if (expr instanceof Binary binary) {
            NexaIr.Value left = expression(binary.left());
            NexaIr.Value right = expression(binary.right());
            NexaType resultType = switch (binary.op()) {
                case "&&", "||", "==", "!=", "<", "<=", ">", ">=" -> NexaType.BOOLEAN;
                default -> common(left.type(), right.type());
            };
            NexaIr.Value result = value(resultType);
            emit(new NexaIr.Binary(result, binary.op(), left, right, binary.span()));
            return result;
        }

        if (expr instanceof Call call) {
            List<NexaIr.Value> args = call.args().stream().map(this::expression).toList();
            String target = symbolicTarget(call.target());

            if (target != null && call.target() instanceof Field) {
                int separator = target.lastIndexOf('.');
                String namespace = target.substring(0, separator);
                String name = target.substring(separator + 1);
                NexaIr.HostCapability capability = new NexaIr.HostCapability(namespace, name, "1");
                NexaIr.HostSignature signature = new NexaIr.HostSignature(
                        args.stream().map(NexaIr.Value::type).toList(), NexaType.OBJECT);
                NexaIr.Value result = value(NexaType.OBJECT);
                emit(new NexaIr.HostCall(result, capability, signature, args, call.span()));
                return result;
            }

            NexaIr.Value result = value(NexaType.OBJECT);
            emit(new NexaIr.Call(result, target == null ? "<dynamic>" : target, args, call.span()));
            return result;
        }

        throw new IllegalStateException("Unsupported Nexa AST expression: " + expr.getClass().getName());
    }

    private NexaType contextualLiteralType(Literal literal, NexaType literalType, NexaType expectedType) {
        if (expectedType == null) return literalType;
        expectedType = resolve(expectedType);
        literalType = resolve(literalType);

        if (NexaType.same(expectedType, literalType)) return expectedType;

        // Integer/float literals are represented by their default frontend
        // type, but the semantic checker has already proved constant narrowing
        // is safe. Carry that declared type into the IR so stores remain typed.
        if (NexaType.numeric(expectedType) && NexaType.numeric(literalType)
                && literal.value() instanceof Number) {
            return expectedType;
        }

        return literalType;
    }

    private NexaType expectedArrayElement(NexaType expectedType) {
        if (expectedType == null) return null;
        expectedType = resolve(expectedType);
        return expectedType instanceof NexaType.Array array ? resolve(array.element()) : null;
    }

    private void store(Expr target, NexaIr.Value value, SourceSpan span) {
        if (target instanceof Var variable) {
            NexaIr.Local local = locals.get(variable.name());
            boolean constant = local != null && local.constant();
            emit(new NexaIr.StoreLocal(null, variable.name(), value, constant, span));
            return;
        }
        if (target instanceof Field field) {
            NexaIr.Value object = expression(field.target());
            emit(new NexaIr.StoreField(null, object, field.name(), value, span));
            return;
        }
        if (target instanceof Index index) {
            NexaIr.Value object = expression(index.target());
            NexaIr.Value key = expression(index.index());
            emit(new NexaIr.StoreIndex(null, object, key, value, span));
            return;
        }
        throw new IllegalStateException("Unsupported assignment target: " + target.getClass().getName());
    }

    private String symbolicTarget(Expr expr) {
        if (expr instanceof Var var) return var.name();
        if (expr instanceof Field field) {
            String prefix = symbolicTarget(field.target());
            return prefix == null ? null : prefix + "." + field.name();
        }
        return null;
    }

    private NexaType fieldType(NexaType target, String field) {
        target = resolve(target);
        if (target instanceof NexaType.ObjectType object) {
            return resolve(object.fields().getOrDefault(field, NexaType.OBJECT));
        }
        return NexaType.OBJECT;
    }

    private NexaType indexType(NexaType target) {
        target = resolve(target);
        if (target instanceof NexaType.Array array) return resolve(array.element());
        return NexaType.OBJECT;
    }

    private NexaType common(NexaType a, NexaType b) {
        if (NexaType.same(a, b)) return a;
        if (!NexaType.numeric(a) || !NexaType.numeric(b)) return NexaType.OBJECT;
        return rank(a) >= rank(b) ? a : b;
    }

    private int rank(NexaType type) {
        return switch (type.displayName()) {
            case "INT8", "UINT8" -> 1;
            case "INT16", "UINT16" -> 2;
            case "INT32", "UINT32" -> 3;
            case "INT64", "UINT64" -> 4;
            case "FLOAT32" -> 5;
            case "FLOAT64" -> 6;
            default -> 0;
        };
    }

    private NexaType resolve(NexaType type) {
        if (type == null) return NexaType.OBJECT;
        if (type instanceof NexaType.Named named) {
            if (named.resolved() != null) return resolve(named.resolved());
            return types.getOrDefault(named.name(), NexaType.OBJECT);
        }
        if (type instanceof NexaType.Array array) return new NexaType.Array(resolve(array.element()));
        if (type instanceof NexaType.ObjectType object) {
            Map<String, NexaType> fields = new LinkedHashMap<>();
            object.fields().forEach((name, fieldType) -> fields.put(name, resolve(fieldType)));
            return new NexaType.ObjectType(fields);
        }
        return type;
    }

    private NexaIr.Value value(NexaType type) {
        return new NexaIr.Value(nextValue++, resolve(type));
    }

    private void emit(NexaIr.Instruction instruction) {
        instructions.add(instruction);
    }

    private int allocateBlock() {
        return nextBlock++;
    }

    private void switchTo(int block) {
        if (currentBlock >= 0 && !terminated) {
            flushCurrentBlock(new NexaIr.Stop(new SourceSpan(0, 0)));
        }
        currentBlock = block;
        instructions.clear();
        terminated = false;
    }

    private void terminate(NexaIr.Terminator terminator) {
        flushCurrentBlock(terminator);
        terminated = true;
    }

    private void finishCurrentBlock(SourceSpan span) {
        if (currentBlock >= 0 && !terminated) {
            flushCurrentBlock(new NexaIr.Stop(span));
            terminated = true;
        }
    }

    private void flushCurrentBlock(NexaIr.Terminator terminator) {
        blocks.add(new NexaIr.Block(currentBlock, instructions, terminator));
        instructions.clear();
    }
}
