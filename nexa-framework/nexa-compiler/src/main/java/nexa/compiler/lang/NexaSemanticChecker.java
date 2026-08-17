package nexa.compiler.lang;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

import nexa.compiler.lang.NexaAst.Array;
import nexa.compiler.lang.NexaAst.Assign;
import nexa.compiler.lang.NexaAst.Binary;
import nexa.compiler.lang.NexaAst.Call;
import nexa.compiler.lang.NexaAst.Expr;
import nexa.compiler.lang.NexaAst.ExprStmt;
import nexa.compiler.lang.NexaAst.Field;
import nexa.compiler.lang.NexaAst.For;
import nexa.compiler.lang.NexaAst.Index;
import nexa.compiler.lang.NexaAst.Let;
import nexa.compiler.lang.NexaAst.Literal;
import nexa.compiler.lang.NexaAst.ObjectLit;
import nexa.compiler.lang.NexaAst.Program;
import nexa.compiler.lang.NexaAst.Return;
import nexa.compiler.lang.NexaAst.Stmt;
import nexa.compiler.lang.NexaAst.TypeDecl;
import nexa.compiler.lang.NexaAst.TagRef;
import nexa.compiler.lang.NexaAst.Unary;
import nexa.compiler.lang.NexaAst.Var;

public final class NexaSemanticChecker {

    public record Diagnostic(String message, SourceSpan span) {
    }

    private final Map<String, NexaType> types = new HashMap<>();
    private final Deque<Map<String, NexaType>> scopes = new ArrayDeque<>();
    private final List<Diagnostic> errors = new ArrayList<>();

    public NexaSemanticChecker() {
        for (NexaType t : List.of(
                NexaType.BOOLEAN,
                NexaType.INT8,
                NexaType.INT16,
                NexaType.INT32,
                NexaType.INT64,
                NexaType.UINT8,
                NexaType.UINT16,
                NexaType.UINT32,
                NexaType.UINT64,
                NexaType.FLOAT32,
                NexaType.FLOAT64,
                NexaType.STRING,
                NexaType.OBJECT,
                NexaType.VOID)) {

            types.put(t.displayName(), t);
        }
    }

    public List<Diagnostic> check(Program program) {
        errors.clear();
        scopes.clear();

        scopes.push(new HashMap<>());

        define("self", NexaType.OBJECT);
        define("input", NexaType.OBJECT);
        define("msg", NexaType.OBJECT);
        define("message", NexaType.OBJECT);

        for (Stmt statement : program.statements()) {
            stmt(statement);
        }

        return List.copyOf(errors);
    }

    // ============================================================
    // Statements
    // ============================================================

    private void stmt(Stmt statement) {

        if (statement instanceof TypeDecl declaration) {

            if (types.containsKey(declaration.name())) {
                bad(
                        "Type already defined: " + declaration.name(),
                        declaration.span());
            } else {
                types.put(
                        declaration.name(),
                        resolve(declaration.type()));
            }

            return;
        }

        if (statement instanceof Let declaration) {

            NexaType declaredType = resolve(declaration.type());

            NexaType valueType = expr(declaration.init());

            require(
                    declaredType,
                    valueType,
                    declaration.init(),
                    "initializer");

            define(
                    declaration.name(),
                    declaredType);

            return;
        }

        if (statement instanceof Assign assignment) {

            if (!lvalue(assignment.target())) {
                bad(
                        "Assignment target is not writable",
                        assignment.target().span());
            }

            NexaType targetType = expr(assignment.target());

            NexaType valueType = expr(assignment.value());

            require(
                    targetType,
                    valueType,
                    assignment.value(),
                    "assignment");

            return;
        }

        if (statement instanceof Return ret) {

            if (ret.value() != null) {
                expr(ret.value());
            }

            return;
        }

        if (statement instanceof ExprStmt expressionStatement) {
            expr(expressionStatement.expr());
            return;
        }

        if (statement instanceof For loop) {

            NexaType iterableType = resolve(expr(loop.iterable()));

            if (!(iterableType instanceof NexaType.Array array)) {

                bad(
                        "'in' requires ARRAY<T>",
                        loop.iterable().span());

                return;
            }

            NexaType declaredLoopType = resolve(loop.declaredType());

            NexaType elementType = resolve(array.element());

            require(
                    declaredLoopType,
                    elementType,
                    loop.iterable(),
                    "loop variable");

            scopes.push(new HashMap<>());

            define(
                    loop.name(),
                    declaredLoopType);

            for (Stmt bodyStatement : loop.body()) {
                stmt(bodyStatement);
            }

            scopes.pop();
        }
    }

    // ============================================================
    // Expressions
    // ============================================================

    private NexaType expr(Expr expression) {

        if (expression instanceof Literal literal) {
            return literal.type();
        }

        if (expression instanceof Var variable) {

            NexaType type = lookupSymbol(variable.name());

            if (type == null) {

                bad(
                        "Unknown variable: " + variable.name(),
                        variable.span());

                return NexaType.OBJECT;
            }

            return resolve(type);
        }

        if (expression instanceof TagRef tag) {
            return NexaType.OBJECT;
        }

        if (expression instanceof Field field) {

            NexaType targetType = resolve(expr(field.target()));

            if (NexaType.same(targetType, NexaType.OBJECT)) {
                return NexaType.OBJECT;
            }

            if (targetType instanceof NexaType.ObjectType objectType) {

                NexaType fieldType = objectType.fields().get(field.name());

                if (fieldType == null) {

                    bad(
                            "Unknown field '" + field.name() + "'",
                            field.span());

                    return NexaType.OBJECT;
                }

                return resolve(fieldType);
            }

            bad(
                    "Field access requires OBJECT/struct",
                    field.span());

            return NexaType.OBJECT;
        }

        if (expression instanceof Index index) {

            NexaType targetType = resolve(expr(index.target()));

            NexaType indexType = expr(index.index());

            if (targetType instanceof NexaType.Array array) {

                require(
                        NexaType.INT64,
                        indexType,
                        index.index(),
                        "array index");

                return resolve(array.element());
            }

            if (NexaType.same(targetType, NexaType.OBJECT)) {
                return NexaType.OBJECT;
            }

            bad(
                    "Indexing requires ARRAY or OBJECT",
                    index.span());

            return NexaType.OBJECT;
        }

        if (expression instanceof Array array) {

            if (array.values().isEmpty()) {
                return new NexaType.Array(NexaType.OBJECT);
            }

            NexaType elementType = null;

            for (Expr element : array.values()) {

                NexaType currentType = expr(element);

                elementType = elementType == null
                        ? currentType
                        : common(
                                elementType,
                                currentType,
                                element.span());
            }

            return new NexaType.Array(
                    elementType == null
                            ? NexaType.OBJECT
                            : elementType);
        }

        if (expression instanceof ObjectLit objectLiteral) {

            Map<String, NexaType> fields = new LinkedHashMap<>();

            for (var entry : objectLiteral.fields().entrySet()) {

                fields.put(
                        entry.getKey(),
                        resolve(expr(entry.getValue())));
            }

            return new NexaType.ObjectType(fields);
        }

        if (expression instanceof Unary unary) {

            NexaType operandType = resolve(expr(unary.expr()));

            if (unary.op().equals("!")) {

                require(
                        NexaType.BOOLEAN,
                        operandType,
                        unary.expr(),
                        "logical negation");

                return NexaType.BOOLEAN;
            }

            if (!NexaType.numeric(operandType)) {

                bad(
                        "Unary numeric operator requires numeric value",
                        unary.span());
            }

            return operandType;
        }

        if (expression instanceof Binary binary) {

            NexaType leftType = resolve(expr(binary.left()));

            NexaType rightType = resolve(expr(binary.right()));

            String operator = binary.op();

            // ----------------------------------------------------
            // Boolean operators
            // ----------------------------------------------------

            if (operator.equals("&&")
                    || operator.equals("||")) {

                require(
                        NexaType.BOOLEAN,
                        leftType,
                        binary.left(),
                        "logical operand");

                require(
                        NexaType.BOOLEAN,
                        rightType,
                        binary.right(),
                        "logical operand");

                return NexaType.BOOLEAN;
            }

            // ----------------------------------------------------
            // Comparisons
            // ----------------------------------------------------

            if (Set.of(
                    "==",
                    "!=",
                    "<",
                    "<=",
                    ">",
                    ">=").contains(operator)) {

                if (!compatible(leftType, rightType)) {

                    bad(
                            "Incompatible comparison types",
                            binary.span());
                }

                return NexaType.BOOLEAN;
            }

            // ----------------------------------------------------
            // Arithmetic
            // ----------------------------------------------------

            if (!NexaType.numeric(leftType)
                    || !NexaType.numeric(rightType)) {

                bad(
                        "Arithmetic requires numeric operands",
                        binary.span());

                return NexaType.OBJECT;
            }

            return common(
                    leftType,
                    rightType,
                    binary.span());
        }

        if (expression instanceof Call call) {

            if (!isDynamicHostCallTarget(call.target())) {
                expr(call.target());
            }

            for (Expr argument : call.args()) {
                expr(argument);
            }

            return NexaType.OBJECT;
        }

        throw new IllegalStateException(
                "Unknown expression: "
                        + expression.getClass().getName());
    }

    // ============================================================
    // Calls
    // ============================================================

    private boolean isDynamicHostCallTarget(Expr target) {

        Expr root = target;

        while (root instanceof Field field) {
            root = field.target();
        }

        return root instanceof Var variable
                && lookupSymbol(variable.name()) == null;
    }

    // ============================================================
    // Type checking
    // ============================================================

    private void require(
            NexaType target,
            NexaType value,
            Expr source,
            String where) {

        target = resolve(target);
        value = resolve(value);

        /*
         * --------------------------------------------------------
         * Exact type match
         * --------------------------------------------------------
         *
         * Exact integer types still need range validation because
         * the parser may represent an integer literal using a
         * wider primitive type.
         *
         * Example:
         *
         * let x: INT8 = 128;
         *
         * The literal may be INT16/INT32 internally, but the
         * semantic target is INT8 and therefore 128 is invalid.
         */
        if (NexaType.same(target, value)) {

            if ((isIntegerType(target) || isFloatType(target))
                    && isConstantExpression(source)
                    && !constantFits(source, target)) {

                bad(
                        "Constant value out of range for "
                                + target.displayName(),
                        source.span());

                return;
            }

            return;
        }

        // --------------------------------------------------------
        // Dynamic OBJECT
        // --------------------------------------------------------

        if (NexaType.same(target, NexaType.OBJECT)) {
            return;
        }

        // --------------------------------------------------------
        // Empty typed array
        // --------------------------------------------------------

        if (target instanceof NexaType.Array
                && source instanceof Array array
                && array.values().isEmpty()) {

            return;
        }

        // --------------------------------------------------------
        // Typed array literal
        // --------------------------------------------------------

        if (target instanceof NexaType.Array targetArray
                && source instanceof Array array) {

            for (Expr element : array.values()) {

                NexaType elementValueType = expr(element);

                require(
                        targetArray.element(),
                        elementValueType,
                        element,
                        where + " array element");
            }

            return;
        }

        // --------------------------------------------------------
        // Dynamic OBJECT -> typed object
        // --------------------------------------------------------

        if (NexaType.same(value, NexaType.OBJECT)
                && target instanceof NexaType.ObjectType) {

            return;
        }

        // --------------------------------------------------------
        // Structural object compatibility
        // --------------------------------------------------------

        if (target instanceof NexaType.ObjectType targetObject
                && value instanceof NexaType.ObjectType valueObject) {

            if (!targetObject.fields()
                    .keySet()
                    .equals(valueObject.fields().keySet())) {

                bad(
                        "Type mismatch in "
                                + where
                                + ": expected "
                                + target.displayName()
                                + ", got "
                                + value.displayName(),
                        source.span());

                return;
            }

            for (String name : targetObject.fields().keySet()) {

                require(
                        targetObject.fields().get(name),
                        valueObject.fields().get(name),
                        source,
                        where + "." + name);
            }

            return;
        }

        // --------------------------------------------------------
        // Constant integer narrowing
        // --------------------------------------------------------
        //
        // Example:
        //
        // let x: INT8 = 100;
        //
        // Parser may infer 100 as INT32, but the constant can be
        // safely narrowed to INT8.
        //
        // Conversely:
        //
        // let x: INT8 = 128;
        //
        // must be rejected.
        //

        if (NexaType.numeric(target)
                && NexaType.numeric(value)
                && isConstantExpression(source)
                && (isIntegerType(target) || isFloatType(target))) {

            if (constantFits(source, target)) {
                return;
            }

            bad(
                    "Constant value out of range for "
                            + target.displayName(),
                    source.span());

            return;
        }

        // --------------------------------------------------------
        // Numeric widening
        // --------------------------------------------------------

        if (NexaType.numeric(target)
                && NexaType.numeric(value)
                && rank(value) <= rank(target)
                && unsignedConversionAllowed(value, target)) {

            return;
        }

        // --------------------------------------------------------
        // Everything else is invalid
        // --------------------------------------------------------

        bad(
                "Type mismatch in "
                        + where
                        + ": expected "
                        + target.displayName()
                        + ", got "
                        + value.displayName(),
                source.span());
    }

    // ============================================================
    // Numeric conversion
    // ============================================================

    private boolean unsignedConversionAllowed(
            NexaType value,
            NexaType target) {

        boolean valueUnsigned = value.displayName().startsWith("UINT");

        boolean targetUnsigned = target.displayName().startsWith("UINT");

        /*
         * UINT -> INT is intentionally not an implicit conversion.
         */
        return !(valueUnsigned && !targetUnsigned);
    }

    private boolean compatible(
            NexaType a,
            NexaType b) {

        a = resolve(a);
        b = resolve(b);

        return NexaType.same(a, b)
                || (NexaType.numeric(a)
                        && NexaType.numeric(b))
                || NexaType.same(a, NexaType.OBJECT)
                || NexaType.same(b, NexaType.OBJECT);
    }

    // ============================================================
    // Constant expressions
    // ============================================================

    private boolean isConstantExpression(Expr expression) {

        if (expression instanceof Literal) {
            return true;
        }

        if (expression instanceof Unary unary) {
            return isConstantExpression(unary.expr());
        }

        if (expression instanceof Binary binary) {
            return isConstantExpression(binary.left())
                    && isConstantExpression(binary.right());
        }

        return false;
    }

    // ============================================================
    // Constant numeric evaluation
    // ============================================================

    private BigDecimal constantNumber(Expr expression) {

        if (expression instanceof Literal literal) {

            Object value = literal.value();

            if (value instanceof Number number) {

                try {
                    return new BigDecimal(
                            number.toString());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }

            /*
             * Some parser implementations preserve the original
             * numeric token as String.
             */
            if (value instanceof String string) {

                try {
                    return new BigDecimal(string);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }

            return null;
        }

        if (expression instanceof Unary unary) {

            BigDecimal value = constantNumber(unary.expr());

            if (value == null) {
                return null;
            }

            return switch (unary.op()) {

                case "+" -> value;

                case "-" -> value.negate();

                default -> null;
            };
        }

        if (expression instanceof Binary binary) {

            BigDecimal left = constantNumber(binary.left());

            BigDecimal right = constantNumber(binary.right());

            if (left == null || right == null) {
                return null;
            }

            return switch (binary.op()) {

                case "+" ->
                    left.add(right);

                case "-" ->
                    left.subtract(right);

                case "*" ->
                    left.multiply(right);

                case "/" -> {

                    if (right.compareTo(
                            BigDecimal.ZERO) == 0) {

                        yield null;
                    }

                    yield left.divide(
                            right,
                            MathContext.DECIMAL128);
                }

                default ->
                    null;
            };
        }

        return null;
    }

    // ============================================================
    // Integer range validation
    // ============================================================

    private boolean isFloatType(NexaType type) {
        return switch (type.displayName()) {
            case "FLOAT32", "FLOAT64" -> true;
            default -> false;
        };
    }

    private boolean isIntegerType(NexaType type) {

        return switch (type.displayName()) {

            case "INT8",
                    "INT16",
                    "INT32",
                    "INT64",
                    "UINT8",
                    "UINT16",
                    "UINT32",
                    "UINT64" ->
                true;

            default -> false;
        };
    }

    private boolean constantFits(
            Expr expression,
            NexaType target) {

        target = resolve(target);

        BigDecimal value = constantNumber(expression);

        if (value == null) {
            return false;
        }

        if (isFloatType(target)) {
            double d = value.doubleValue();
            if (target.displayName().equals("FLOAT32")) {
                float f = (float) d;
                return Float.isFinite(f);
            }
            return Double.isFinite(d);
        }

        if (!isIntegerType(target)) {
            return false;
        }

        /*
         * Integer target cannot receive a fractional constant.
         *
         * Examples:
         *
         * INT32 = 1.5 -> reject
         * INT32 = 1.0 -> accept
         */
        if (value.stripTrailingZeros().scale() > 0) {
            return false;
        }

        return switch (target.displayName()) {

            case "INT8" ->

                value.compareTo(
                        BigDecimal.valueOf(-128)) >= 0

                        &&

                        value.compareTo(
                                BigDecimal.valueOf(127)) <= 0;

            case "INT16" ->

                value.compareTo(
                        BigDecimal.valueOf(-32768)) >= 0

                        &&

                        value.compareTo(
                                BigDecimal.valueOf(32767)) <= 0;

            case "INT32" ->

                value.compareTo(
                        BigDecimal.valueOf(
                                Integer.MIN_VALUE)) >= 0

                        &&

                        value.compareTo(
                                BigDecimal.valueOf(
                                        Integer.MAX_VALUE)) <= 0;

            case "INT64" ->

                value.compareTo(
                        BigDecimal.valueOf(
                                Long.MIN_VALUE)) >= 0

                        &&

                        value.compareTo(
                                BigDecimal.valueOf(
                                        Long.MAX_VALUE)) <= 0;

            case "UINT8" ->

                value.compareTo(
                        BigDecimal.ZERO) >= 0

                        &&

                        value.compareTo(
                                BigDecimal.valueOf(255)) <= 0;

            case "UINT16" ->

                value.compareTo(
                        BigDecimal.ZERO) >= 0

                        &&

                        value.compareTo(
                                BigDecimal.valueOf(65535)) <= 0;

            case "UINT32" ->

                value.compareTo(
                        BigDecimal.ZERO) >= 0

                        &&

                        value.compareTo(
                                new BigDecimal(
                                        "4294967295")) <= 0;

            case "UINT64" ->

                value.compareTo(
                        BigDecimal.ZERO) >= 0

                        &&

                        value.compareTo(
                                new BigDecimal(
                                        "18446744073709551615")) <= 0;

            default ->
                false;
        };
    }

    // ============================================================
    // Type inference
    // ============================================================

    private NexaType common(
            NexaType a,
            NexaType b,
            SourceSpan span) {

        a = resolve(a);
        b = resolve(b);

        if (NexaType.same(a, b)) {
            return a;
        }

        if (NexaType.numeric(a)
                && NexaType.numeric(b)) {

            if (a.displayName().equals("FLOAT64")
                    || b.displayName().equals("FLOAT64")) {

                return NexaType.FLOAT64;
            }

            if (a.displayName().equals("FLOAT32")
                    || b.displayName().equals("FLOAT32")) {

                return NexaType.FLOAT32;
            }

            return rank(a) >= rank(b)
                    ? a
                    : b;
        }

        bad(
                "No compatible types: "
                        + a.displayName()
                        + " and "
                        + b.displayName(),
                span);

        return NexaType.OBJECT;
    }

    private int rank(NexaType type) {

        return switch (type.displayName()) {

            case "INT8",
                    "UINT8" ->
                1;

            case "INT16",
                    "UINT16" ->
                2;

            case "INT32",
                    "UINT32" ->
                3;

            case "INT64",
                    "UINT64" ->
                4;

            case "FLOAT32" -> 5;

            case "FLOAT64" -> 6;

            default -> 0;
        };
    }

    // ============================================================
    // Type resolution
    // ============================================================

    private NexaType resolve(NexaType type) {

        if (type instanceof NexaType.Named named) {

            NexaType resolved = types.get(named.name());

            if (resolved == null) {

                bad(
                        "Unknown type: " + named.name(),
                        new SourceSpan(0, 0));

                return NexaType.OBJECT;
            }

            return resolved;
        }

        if (type instanceof NexaType.Array array) {

            return new NexaType.Array(
                    resolve(array.element()));
        }

        if (type instanceof NexaType.ObjectType object) {

            Map<String, NexaType> fields = new LinkedHashMap<>();

            object.fields().forEach(
                    (name, fieldType) -> fields.put(
                            name,
                            resolve(fieldType)));

            return new NexaType.ObjectType(fields);
        }

        return type;
    }

    // ============================================================
    // LValues
    // ============================================================

    private boolean lvalue(Expr expression) {

        return expression instanceof Var
                || expression instanceof TagRef
                || expression instanceof Field
                || expression instanceof Index;
    }

    // ============================================================
    // Scope
    // ============================================================

    private void define(
            String name,
            NexaType type) {

        scopes.peek().put(name, type);
    }

    private NexaType lookupSymbol(String name) {

        for (Map<String, NexaType> scope : scopes) {

            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }

        return null;
    }

    private NexaType lookup(String name) {
        return lookupSymbol(name);
    }

    // ============================================================
    // Diagnostics
    // ============================================================

    private void bad(
            String message,
            SourceSpan span) {

        errors.add(
                new Diagnostic(
                        message,
                        span));
    }
}