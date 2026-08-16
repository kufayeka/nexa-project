package nexa.compiler.lang;

import java.util.*;
import nexa.compiler.lang.NexaAst.*;

/** Semantic analysis for the Nexa source language. */
public final class NexaSemanticChecker {
    public record Diagnostic(String message, SourceSpan span) {}
    private record Symbol(NexaType type, boolean constant) {}

    private final Map<String, NexaType> types = new HashMap<>();
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();
    private final List<Diagnostic> errors = new ArrayList<>();

    public NexaSemanticChecker() {
        for (NexaType t : List.of(
                NexaType.BOOLEAN, NexaType.INT8, NexaType.INT16, NexaType.INT32, NexaType.INT64,
                NexaType.UINT8, NexaType.UINT16, NexaType.UINT32, NexaType.UINT64,
                NexaType.FLOAT32, NexaType.FLOAT64, NexaType.STRING, NexaType.OBJECT, NexaType.VOID)) {
            types.put(t.displayName(), t);
        }
    }

    public List<Diagnostic> check(Program program) {
        errors.clear();
        scopes.clear();
        scopes.push(new HashMap<>());
        define("self", NexaType.OBJECT, false);
        define("input", NexaType.OBJECT, false);
        for (Stmt statement : program.statements()) stmt(statement);
        return List.copyOf(errors);
    }

    private void stmt(Stmt statement) {
        if (statement instanceof TypeDecl declaration) {
            if (types.containsKey(declaration.name())) {
                bad("Type already defined: " + declaration.name(), declaration.span());
            } else {
                types.put(declaration.name(), resolve(declaration.type()));
            }
            return;
        }

        if (statement instanceof Let declaration) {
            NexaType declaredType = resolve(declaration.type());
            NexaType valueType = expr(declaration.init());
            require(declaredType, valueType, declaration.init(), "initializer");
            define(declaration.name(), declaredType, declaration.constant());
            return;
        }

        if (statement instanceof Assign assignment) {
            if (!lvalue(assignment.target())) {
                bad("Assignment target is not writable", assignment.target().span());
                return;
            }

            if (assignment.target() instanceof Var variable) {
                Symbol symbol = lookupSymbol(variable.name());
                if (symbol != null && symbol.constant()) {
                    bad("Cannot assign to constant: " + variable.name(), variable.span());
                    expr(assignment.value());
                    return;
                }
            }

            NexaType targetType = expr(assignment.target());
            NexaType valueType = expr(assignment.value());
            require(targetType, valueType, assignment.value(), "assignment");
            return;
        }

        if (statement instanceof Return ret) {
            expr(ret.value());
            return;
        }

        if (statement instanceof ExprStmt expressionStatement) {
            expr(expressionStatement.expr());
            return;
        }

        if (statement instanceof For loop) {
            NexaType iterableType = resolve(expr(loop.iterable()));
            if (!(iterableType instanceof NexaType.Array array)) {
                bad("'in' requires ARRAY<T>", loop.iterable().span());
                return;
            }

            NexaType declaredLoopType = resolve(loop.declaredType());
            require(declaredLoopType, resolve(array.element()), loop.iterable(), "loop variable");

            scopes.push(new HashMap<>());
            define(loop.name(), declaredLoopType, false);
            for (Stmt bodyStatement : loop.body()) stmt(bodyStatement);
            scopes.pop();
            return;
        }

        throw new IllegalStateException("Unknown statement: " + statement.getClass().getName());
    }

    private NexaType expr(Expr expression) {
        if (expression instanceof Literal literal) {
            return resolve(literal.type());
        }

        if (expression instanceof Var variable) {
            Symbol symbol = lookupSymbol(variable.name());
            if (symbol == null) {
                bad("Unknown variable: " + variable.name(), variable.span());
                return NexaType.OBJECT;
            }
            return resolve(symbol.type());
        }

        if (expression instanceof Field field) {
            NexaType targetType = resolve(expr(field.target()));
            if (NexaType.same(targetType, NexaType.OBJECT)) return NexaType.OBJECT;

            if (targetType instanceof NexaType.ObjectType objectType) {
                NexaType fieldType = objectType.fields().get(field.name());
                if (fieldType == null) {
                    bad("Unknown field '" + field.name() + "'", field.span());
                    return NexaType.OBJECT;
                }
                return resolve(fieldType);
            }

            bad("Field access requires OBJECT/struct", field.span());
            return NexaType.OBJECT;
        }

        if (expression instanceof Index index) {
            NexaType targetType = resolve(expr(index.target()));
            NexaType indexType = expr(index.index());

            if (targetType instanceof NexaType.Array array) {
                require(NexaType.INT64, indexType, index.index(), "array index");
                return resolve(array.element());
            }

            if (NexaType.same(targetType, NexaType.OBJECT)) return NexaType.OBJECT;

            bad("Indexing requires ARRAY or OBJECT", index.span());
            return NexaType.OBJECT;
        }

        if (expression instanceof Array array) {
            if (array.values().isEmpty()) return new NexaType.Array(NexaType.OBJECT);

            NexaType elementType = null;
            for (Expr element : array.values()) {
                NexaType current = expr(element);
                elementType = elementType == null
                        ? current
                        : common(elementType, current, element, elementType, current);
            }

            return new NexaType.Array(elementType == null ? NexaType.OBJECT : elementType);
        }

        if (expression instanceof ObjectLit objectLiteral) {
            Map<String, NexaType> fields = new LinkedHashMap<>();
            for (var entry : objectLiteral.fields().entrySet()) {
                fields.put(entry.getKey(), resolve(expr(entry.getValue())));
            }
            return new NexaType.ObjectType(fields);
        }

        if (expression instanceof Unary unary) {
            NexaType operandType = resolve(expr(unary.expr()));

            if (unary.op().equals("!")) {
                require(NexaType.BOOLEAN, operandType, unary.expr(), "logical negation");
                return NexaType.BOOLEAN;
            }

            if (!NexaType.numeric(operandType)) {
                bad("Unary numeric operator requires numeric value", unary.span());
                return NexaType.OBJECT;
            }

            return operandType;
        }

        if (expression instanceof Binary binary) {
            NexaType leftType = resolve(expr(binary.left()));
            NexaType rightType = resolve(expr(binary.right()));
            String operator = binary.op();

            if (operator.equals("&&") || operator.equals("||")) {
                require(NexaType.BOOLEAN, leftType, binary.left(), "logical operand");
                require(NexaType.BOOLEAN, rightType, binary.right(), "logical operand");
                return NexaType.BOOLEAN;
            }

            if (Set.of("==", "!=", "<", "<=", ">", ">=").contains(operator)) {
                if (!compatible(leftType, rightType)) {
                    bad("Incompatible comparison types", binary.span());
                }
                return NexaType.BOOLEAN;
            }

            if (!NexaType.numeric(leftType) || !NexaType.numeric(rightType)) {
                bad("Arithmetic requires numeric operands", binary.span());
                return NexaType.OBJECT;
            }

            return common(leftType, rightType, binary.span(), binary.left(), binary.right());
        }

        if (expression instanceof Call call) {
            // Calls can target either a source-language value or an unresolved
            // host capability. Host capabilities intentionally cross the
            // semantic boundary here and are resolved by the runtime/plugin
            // registry later.
            //
            // Examples:
            //   someFunction(10)
            //   mqtt.publish(topic, payload)
            //   opcua.client.read(nodeId)
            //
            // A declared source variable is still checked normally:
            //   client.publish(...)
            //   input.client.publish(...)
            if (!isDynamicHostCallTarget(call.target())) {
                expr(call.target());
            }

            for (Expr argument : call.args()) {
                expr(argument);
            }

            // Function signatures are resolved by the host/function registry
            // in the later compilation/runtime phase.
            return NexaType.OBJECT;
        }

        throw new IllegalStateException("Unknown expression: " + expression.getClass().getName());
    }

    /**
     * Determines whether a call target belongs to the unresolved host
     * capability namespace rather than the source-language symbol table.
     *
     * The important distinction is that this method is evaluated only for a
     * Call target. An unknown identifier used anywhere else is still a normal
     * semantic error.
     *
     * Accepted dynamic forms:
     *   foo(...)
     *   mqtt.publish(...)
     *   opcua.client.read(...)
     *
     * Still checked as source expressions:
     *   client.publish(...)       // `client` is declared
     *   input.client.publish(...) // `input` is declared
     */
    private boolean isDynamicHostCallTarget(Expr target) {
        Expr root = target;

        while (root instanceof Field field) {
            root = field.target();
        }

        if (!(root instanceof Var variable)) {
            return false;
        }

        // Any unresolved root is a symbolic capability/function name when it
        // appears as a call target. This deliberately does NOT make unknown
        // variables generally valid; only the Call expression gets this rule.
        return lookupSymbol(variable.name()) == null;
    }

    private void require(NexaType target, NexaType value, Expr source, String where) {
        target = resolve(target);
        value = resolve(value);
        if (NexaType.same(target, value)) return;

        if (target instanceof NexaType.Array
                && source instanceof Array array
                && array.values().isEmpty()) return;

        if (target instanceof NexaType.Array targetArray && source instanceof Array array) {
            for (Expr element : array.values()) {
                require(targetArray.element(), expr(element), element, where + " array element");
            }
            return;
        }

        if (NexaType.same(value, NexaType.OBJECT) && target instanceof NexaType.ObjectType) return;
        if (NexaType.same(target, NexaType.OBJECT) && value instanceof NexaType.ObjectType) return;

        if (target instanceof NexaType.ObjectType targetObject
                && value instanceof NexaType.ObjectType valueObject) {
            if (!targetObject.fields().keySet().equals(valueObject.fields().keySet())) {
                bad("Type mismatch in " + where + ": expected "
                        + target.displayName() + ", got " + value.displayName(), source.span());
                return;
            }

            for (String name : targetObject.fields().keySet()) {
                require(targetObject.fields().get(name), valueObject.fields().get(name), source, where + "." + name);
            }
            return;
        }

        if (isConstantExpression(source) && constantFits(source, target)) return;

        if (NexaType.numeric(target)
                && NexaType.numeric(value)
                && rank(value) <= rank(target)
                && unsignedConversionAllowed(value, target)) return;

        bad("Type mismatch in " + where + ": expected "
                + target.displayName() + ", got " + value.displayName(), source.span());
    }

    private boolean unsignedConversionAllowed(NexaType value, NexaType target) {
        boolean vu = value.displayName().startsWith("UINT");
        boolean tu = target.displayName().startsWith("UINT");
        return !(vu && !tu);
    }

    private boolean compatible(NexaType a, NexaType b) {
        a = resolve(a);
        b = resolve(b);
        if (NexaType.same(a, b)) return true;
        if (NexaType.numeric(a) && NexaType.numeric(b)) return true;
        return NexaType.same(a, NexaType.OBJECT) || NexaType.same(b, NexaType.OBJECT);
    }

    private boolean isConstantExpression(Expr expression) {
        if (expression instanceof Literal) return true;
        if (expression instanceof Unary unary) return isConstantExpression(unary.expr());
        if (expression instanceof Binary binary) {
            return isConstantExpression(binary.left()) && isConstantExpression(binary.right());
        }
        return false;
    }

    private boolean constantFits(Expr expression, NexaType target) {
        target = resolve(target);
        if (!NexaType.numeric(target)) return false;

        Number value = constantNumber(expression);
        if (value == null) return false;

        double n = value.doubleValue();
        if (!Double.isFinite(n)) return false;
        if (requiresWholeNumber(target) && !isWhole(n)) return false;

        return switch (target.displayName()) {
            case "INT8" -> n >= -128 && n <= 127;
            case "INT16" -> n >= -32768 && n <= 32767;
            case "INT32" -> n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE;
            case "INT64" -> true;
            case "UINT8" -> n >= 0 && n <= 255;
            case "UINT16" -> n >= 0 && n <= 65535;
            case "UINT32" -> n >= 0 && n <= 4294967295d;
            case "UINT64" -> n >= 0;
            case "FLOAT32", "FLOAT64" -> true;
            default -> false;
        };
    }

    private boolean requiresWholeNumber(NexaType type) {
        return switch (type.displayName()) {
            case "INT8", "INT16", "INT32", "INT64",
                    "UINT8", "UINT16", "UINT32", "UINT64" -> true;
            default -> false;
        };
    }

    private Number constantNumber(Expr expression) {
        if (expression instanceof Literal literal && literal.value() instanceof Number n) return n;

        if (expression instanceof Unary unary) {
            Number n = constantNumber(unary.expr());
            if (n == null) return null;
            return switch (unary.op()) {
                case "+" -> n.doubleValue();
                case "-" -> -n.doubleValue();
                default -> null;
            };
        }

        if (expression instanceof Binary binary) {
            Number l = constantNumber(binary.left());
            Number r = constantNumber(binary.right());
            if (l == null || r == null) return null;

            double a = l.doubleValue();
            double b = r.doubleValue();
            return switch (binary.op()) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> b == 0 ? null : a / b;
                default -> null;
            };
        }

        return null;
    }

    private boolean isWhole(double value) { return value == Math.rint(value); }

    private NexaType common(NexaType a, NexaType b, SourceSpan span, Expr leftExpr, Expr rightExpr) {
        a = resolve(a);
        b = resolve(b);
        if (NexaType.same(a, b)) return a;

        if (leftExpr instanceof Literal literal
                && !(rightExpr instanceof Literal)
                && literalFits(literal, b)) return b;

        if (rightExpr instanceof Literal literal
                && !(leftExpr instanceof Literal)
                && literalFits(literal, a)) return a;

        if (NexaType.numeric(a) && NexaType.numeric(b)) {
            if (a.displayName().equals("FLOAT64") || b.displayName().equals("FLOAT64")) return NexaType.FLOAT64;
            if (a.displayName().equals("FLOAT32") || b.displayName().equals("FLOAT32")) return NexaType.FLOAT32;
            return rank(a) >= rank(b) ? a : b;
        }

        bad("No compatible types: " + a.displayName() + " and " + b.displayName(), span);
        return NexaType.OBJECT;
    }

    private NexaType common(NexaType a, NexaType b, Expr source, NexaType ignoredLeft, NexaType ignoredRight) {
        return common(a, b, source.span(), source, source);
    }

    private boolean literalFits(Literal literal, NexaType target) {
        target = resolve(target);
        if (!(literal.value() instanceof Number number) || !NexaType.numeric(target)) return false;

        double n = number.doubleValue();
        if (!Double.isFinite(n)) return false;
        if (requiresWholeNumber(target) && !isWhole(n)) return false;

        return switch (target.displayName()) {
            case "INT8" -> n >= -128 && n <= 127;
            case "INT16" -> n >= -32768 && n <= 32767;
            case "INT32" -> n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE;
            case "INT64" -> true;
            case "UINT8" -> n >= 0 && n <= 255;
            case "UINT16" -> n >= 0 && n <= 65535;
            case "UINT32" -> n >= 0 && n <= 4294967295d;
            case "UINT64" -> n >= 0;
            case "FLOAT32", "FLOAT64" -> true;
            default -> false;
        };
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
        if (type instanceof NexaType.Named named) {
            NexaType resolved = types.get(named.name());
            if (resolved == null) {
                bad("Unknown type: " + named.name(), new SourceSpan(0, 0));
                return NexaType.OBJECT;
            }
            return resolve(resolved);
        }

        if (type instanceof NexaType.Array array) {
            return new NexaType.Array(resolve(array.element()));
        }

        if (type instanceof NexaType.ObjectType object) {
            Map<String, NexaType> fields = new LinkedHashMap<>();
            object.fields().forEach((name, fieldType) -> fields.put(name, resolve(fieldType)));
            return new NexaType.ObjectType(fields);
        }

        return type;
    }

    private boolean lvalue(Expr expression) {
        return expression instanceof Var || expression instanceof Field || expression instanceof Index;
    }

    private void define(String name, NexaType type, boolean constant) {
        scopes.peek().put(name, new Symbol(type, constant));
    }

    private Symbol lookupSymbol(String name) {
        for (Map<String, Symbol> scope : scopes) {
            if (scope.containsKey(name)) return scope.get(name);
        }
        return null;
    }

    private void bad(String message, SourceSpan span) {
        errors.add(new Diagnostic(message, span));
    }
}
