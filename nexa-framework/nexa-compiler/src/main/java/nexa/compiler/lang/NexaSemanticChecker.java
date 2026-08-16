package nexa.compiler.lang;

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
import nexa.compiler.lang.NexaAst.Unary;
import nexa.compiler.lang.NexaAst.Var;

public final class NexaSemanticChecker {

    public record Diagnostic(String message, SourceSpan span) {
    }

    private final Map<String, NexaType> types = new HashMap<>();
    private final Deque<Map<String, NexaType>> scopes = new ArrayDeque<>();
    private final List<Diagnostic> errors = new ArrayList<>();

    public NexaSemanticChecker() {
        registerPrimitiveTypes();
    }

    private void registerPrimitiveTypes() {
        for (NexaType type : List.of(
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

            types.put(type.displayName(), type);
        }
    }

    // ========================================================================
    // Entry point
    // ========================================================================

    public List<Diagnostic> check(Program program) {

        errors.clear();
        scopes.clear();

        scopes.push(new HashMap<>());

        /*
         * Runtime globals.
         *
         * self -> current runtime object
         * input -> dynamic runtime input
         */
        define("self", NexaType.OBJECT);
        define("input", NexaType.OBJECT);

        for (Stmt statement : program.statements()) {
            stmt(statement);
        }

        return List.copyOf(errors);
    }

    // ========================================================================
    // Statements
    // ========================================================================

    private void stmt(Stmt statement) {

        // --------------------------------------------------------------------
        // User defined type
        // --------------------------------------------------------------------

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

        // --------------------------------------------------------------------
        // Variable declaration
        // --------------------------------------------------------------------

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

        // --------------------------------------------------------------------
        // Assignment
        // --------------------------------------------------------------------

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

        // --------------------------------------------------------------------
        // Return
        // --------------------------------------------------------------------

        if (statement instanceof Return ret) {

            expr(ret.value());

            return;
        }

        // --------------------------------------------------------------------
        // Expression statement
        // --------------------------------------------------------------------

        if (statement instanceof ExprStmt expressionStatement) {

            expr(expressionStatement.expr());

            return;
        }

        // --------------------------------------------------------------------
        // For loop
        // --------------------------------------------------------------------

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

            /*
             * Loop variable has its own scope.
             */
            scopes.push(new HashMap<>());

            define(
                    loop.name(),
                    declaredLoopType);

            for (Stmt bodyStatement : loop.body()) {
                stmt(bodyStatement);
            }

            scopes.pop();

            return;
        }

        throw new IllegalStateException(
                "Unknown statement: "
                        + statement.getClass().getName());
    }

    // ========================================================================
    // Expressions
    // ========================================================================

    private NexaType expr(Expr expression) {

        // --------------------------------------------------------------------
        // Literal
        // --------------------------------------------------------------------

        if (expression instanceof Literal literal) {

            return resolve(literal.type());
        }

        // --------------------------------------------------------------------
        // Variable
        // --------------------------------------------------------------------

        if (expression instanceof Var variable) {

            NexaType type = lookup(variable.name());

            if (type == null) {

                bad(
                        "Unknown variable: " + variable.name(),
                        variable.span());

                return NexaType.OBJECT;
            }

            return resolve(type);
        }

        // --------------------------------------------------------------------
        // Field access
        // --------------------------------------------------------------------

        if (expression instanceof Field field) {

            NexaType targetType = resolve(expr(field.target()));

            /*
             * Dynamic OBJECT.
             *
             * We cannot know the field type statically.
             */
            if (NexaType.same(
                    targetType,
                    NexaType.OBJECT)) {

                return NexaType.OBJECT;
            }

            if (targetType instanceof NexaType.ObjectType objectType) {

                NexaType fieldType = objectType.fields()
                        .get(field.name());

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

        // --------------------------------------------------------------------
        // Array/object indexing
        // --------------------------------------------------------------------

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

            /*
             * Dynamic OBJECT indexing.
             */
            if (NexaType.same(
                    targetType,
                    NexaType.OBJECT)) {

                return NexaType.OBJECT;
            }

            bad(
                    "Indexing requires ARRAY or OBJECT",
                    index.span());

            return NexaType.OBJECT;
        }

        // --------------------------------------------------------------------
        // Array literal
        // --------------------------------------------------------------------

        if (expression instanceof Array array) {

            if (array.values().isEmpty()) {

                /*
                 * Empty arrays are contextual.
                 *
                 * Example:
                 *
                 * ARRAY<INT32> = []
                 */
                return new NexaType.Array(
                        NexaType.OBJECT);
            }

            NexaType elementType = null;

            for (Expr element : array.values()) {

                NexaType currentType = expr(element);

                if (elementType == null) {

                    elementType = currentType;

                } else {

                    elementType = common(
                            elementType,
                            currentType,
                            element,
                            elementType,
                            currentType);
                }
            }

            return new NexaType.Array(
                    elementType == null
                            ? NexaType.OBJECT
                            : elementType);
        }

        // --------------------------------------------------------------------
        // Object literal
        // --------------------------------------------------------------------

        if (expression instanceof ObjectLit objectLiteral) {

            Map<String, NexaType> fields = new LinkedHashMap<>();

            for (var entry : objectLiteral.fields().entrySet()) {

                fields.put(
                        entry.getKey(),
                        resolve(
                                expr(entry.getValue())));
            }

            return new NexaType.ObjectType(fields);
        }

        // --------------------------------------------------------------------
        // Unary
        // --------------------------------------------------------------------

        if (expression instanceof Unary unary) {

            NexaType operandType = resolve(expr(unary.expr()));

            // Logical NOT
            if (unary.op().equals("!")) {

                require(
                        NexaType.BOOLEAN,
                        operandType,
                        unary.expr(),
                        "logical negation");

                return NexaType.BOOLEAN;
            }

            // Numeric + / -
            if (!NexaType.numeric(operandType)) {

                bad(
                        "Unary numeric operator requires numeric value",
                        unary.span());

                return NexaType.OBJECT;
            }

            return operandType;
        }

        // --------------------------------------------------------------------
        // Binary
        // --------------------------------------------------------------------

        if (expression instanceof Binary binary) {

            NexaType leftType = resolve(expr(binary.left()));

            NexaType rightType = resolve(expr(binary.right()));

            String operator = binary.op();

            // ----------------------------------------------------------------
            // Logical
            // ----------------------------------------------------------------

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

            // ----------------------------------------------------------------
            // Comparison
            // ----------------------------------------------------------------

            if (Set.of(
                    "==",
                    "!=",
                    "<",
                    "<=",
                    ">",
                    ">=").contains(operator)) {

                if (!compatible(
                        leftType,
                        rightType)) {

                    bad(
                            "Incompatible comparison types",
                            binary.span());
                }

                return NexaType.BOOLEAN;
            }

            // ----------------------------------------------------------------
            // Arithmetic
            // ----------------------------------------------------------------

            if (!NexaType.numeric(leftType)
                    || !NexaType.numeric(rightType)) {

                bad(
                        "Arithmetic requires numeric operands",
                        binary.span());

                return NexaType.OBJECT;
            }

            /*
             * IMPORTANT:
             *
             * A numeric literal is context-sensitive.
             *
             * Example:
             *
             * let x: INT32 = 10;
             * let y: INT32 = x + 2;
             *
             * Literal 2 is internally INT64, but it fits INT32.
             *
             * Therefore:
             *
             * x + 2
             *
             * must be INT32, NOT INT64.
             */
            return common(
                    leftType,
                    rightType,
                    binary.span(),
                    binary.left(),
                    binary.right());
        }

        // --------------------------------------------------------------------
        // Function call
        // --------------------------------------------------------------------

        if (expression instanceof Call call) {

            /*
             * Do NOT resolve a simple call target as a variable.
             *
             * These are valid syntactically even though function
             * signatures are not registered yet:
             *
             * someFunction()
             * foo(bar())
             * readTag("motor.speed")
             *
             * Function resolution will be handled by the function
             * registry / runtime API layer later.
             */
            if (!(call.target() instanceof Var)) {
                expr(call.target());
            }

            for (Expr argument : call.args()) {
                expr(argument);
            }

            /*
             * Function signatures are intentionally unresolved for now.
             */
            return NexaType.OBJECT;
        }

        throw new IllegalStateException(
                "Unknown expression: "
                        + expression.getClass().getName());
    }

    // ========================================================================
    // Type compatibility
    // ========================================================================

    private void require(
            NexaType target,
            NexaType value,
            Expr source,
            String where) {

        target = resolve(target);
        value = resolve(value);

        // --------------------------------------------------------------------
        // Exact match
        // --------------------------------------------------------------------

        if (NexaType.same(target, value)) {
            return;
        }

        // --------------------------------------------------------------------
        // Empty array adopts target type
        // --------------------------------------------------------------------

        if (target instanceof NexaType.Array
                && source instanceof Array array
                && array.values().isEmpty()) {

            return;
        }

        // --------------------------------------------------------------------
        // Array literal contextual typing
        // --------------------------------------------------------------------

        if (target instanceof NexaType.Array targetArray
                && source instanceof Array array) {

            for (Expr element : array.values()) {

                NexaType actual = expr(element);

                require(
                        targetArray.element(),
                        actual,
                        element,
                        where + " array element");
            }

            return;
        }

        // --------------------------------------------------------------------
        // Dynamic OBJECT -> typed object
        //
        // Example:
        //
        // let motor: Motor = input;
        //
        // Runtime validation is expected.
        // --------------------------------------------------------------------

        if (NexaType.same(
                value,
                NexaType.OBJECT)
                && target instanceof NexaType.ObjectType) {

            return;
        }

        // --------------------------------------------------------------------
        // Typed object -> generic OBJECT
        // --------------------------------------------------------------------

        if (NexaType.same(
                target,
                NexaType.OBJECT)
                && value instanceof NexaType.ObjectType) {

            return;
        }

        // --------------------------------------------------------------------
        // Typed object structural compatibility
        // --------------------------------------------------------------------

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

            for (String fieldName : targetObject.fields().keySet()) {

                require(
                        targetObject.fields().get(fieldName),
                        valueObject.fields().get(fieldName),
                        source,
                        where + "." + fieldName);
            }

            return;
        }

        // --------------------------------------------------------------------
        // Constant expression narrowing
        //
        // Example:
        //
        // INT8 = 100
        // INT16 = 1000
        // INT32 = 1 + 2
        // INT32 = -100
        //
        // But:
        //
        // INT64 a = 100;
        // INT8 b = a;
        //
        // remains invalid.
        // --------------------------------------------------------------------

        if (isConstantExpression(source)
                && constantFits(
                        source,
                        target)) {

            return;
        }

        // --------------------------------------------------------------------
        // Numeric widening
        // --------------------------------------------------------------------

        if (NexaType.numeric(target)
                && NexaType.numeric(value)
                && rank(value) <= rank(target)
                && unsignedConversionAllowed(
                        value,
                        target)) {

            return;
        }

        // --------------------------------------------------------------------
        // Invalid
        // --------------------------------------------------------------------

        bad(
                "Type mismatch in "
                        + where
                        + ": expected "
                        + target.displayName()
                        + ", got "
                        + value.displayName(),
                source.span());
    }

    private boolean unsignedConversionAllowed(
            NexaType value,
            NexaType target) {

        boolean valueUnsigned = value.displayName()
                .startsWith("UINT");

        boolean targetUnsigned = target.displayName()
                .startsWith("UINT");

        /*
         * Do not silently convert unsigned -> signed.
         */
        if (valueUnsigned && !targetUnsigned) {
            return false;
        }

        return true;
    }

    private boolean compatible(
            NexaType a,
            NexaType b) {

        a = resolve(a);
        b = resolve(b);

        if (NexaType.same(a, b)) {
            return true;
        }

        if (NexaType.numeric(a)
                && NexaType.numeric(b)) {

            return true;
        }

        /*
         * Dynamic OBJECT is intentionally compatible with
         * unknown runtime values.
         */
        if (NexaType.same(a, NexaType.OBJECT)
                || NexaType.same(b, NexaType.OBJECT)) {

            return true;
        }

        return false;
    }

    // ========================================================================
    // Constant expressions
    // ========================================================================

    private boolean isConstantExpression(
            Expr expression) {

        if (expression instanceof Literal) {
            return true;
        }

        if (expression instanceof Unary unary) {

            return isConstantExpression(
                    unary.expr());
        }

        if (expression instanceof Binary binary) {

            return isConstantExpression(
                    binary.left())
                    && isConstantExpression(
                            binary.right());
        }

        return false;
    }

    private boolean constantFits(
            Expr expression,
            NexaType target) {

        target = resolve(target);

        if (!NexaType.numeric(target)) {
            return false;
        }

        Number value = constantNumber(expression);

        if (value == null) {
            return false;
        }

        double numericValue = value.doubleValue();

        if (!Double.isFinite(numericValue)) {
            return false;
        }

        if (requiresWholeNumber(target)
                && !isWhole(numericValue)) {

            return false;
        }

        return switch (target.displayName()) {

            case "INT8" ->
                numericValue >= -128
                        && numericValue <= 127;

            case "INT16" ->
                numericValue >= -32768
                        && numericValue <= 32767;

            case "INT32" ->
                numericValue >= Integer.MIN_VALUE
                        && numericValue <= Integer.MAX_VALUE;

            case "INT64" ->
                true;

            case "UINT8" ->
                numericValue >= 0
                        && numericValue <= 255;

            case "UINT16" ->
                numericValue >= 0
                        && numericValue <= 65535;

            case "UINT32" ->
                numericValue >= 0
                        && numericValue <= 4294967295d;

            case "UINT64" ->
                numericValue >= 0;

            case "FLOAT32",
                    "FLOAT64" ->
                true;

            default ->
                false;
        };
    }

    private boolean requiresWholeNumber(
            NexaType type) {

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

            default ->
                false;
        };
    }

    private Number constantNumber(
            Expr expression) {

        if (expression instanceof Literal literal
                && literal.value() instanceof Number number) {

            return number;
        }

        if (expression instanceof Unary unary) {

            Number value = constantNumber(
                    unary.expr());

            if (value == null) {
                return null;
            }

            return switch (unary.op()) {

                case "+" ->
                    value.doubleValue();

                case "-" ->
                    -value.doubleValue();

                default ->
                    null;
            };
        }

        if (expression instanceof Binary binary) {

            Number left = constantNumber(
                    binary.left());

            Number right = constantNumber(
                    binary.right());

            if (left == null
                    || right == null) {

                return null;
            }

            double l = left.doubleValue();

            double r = right.doubleValue();

            return switch (binary.op()) {

                case "+" ->
                    l + r;

                case "-" ->
                    l - r;

                case "*" ->
                    l * r;

                case "/" -> {

                    if (r == 0) {
                        yield null;
                    }

                    yield l / r;
                }

                default ->
                    null;
            };
        }

        return null;
    }

    private boolean isWhole(
            double value) {

        return value == Math.rint(value);
    }

    // ========================================================================
    // Numeric common type
    // ========================================================================

    /**
     * Determine the result type of a binary arithmetic expression.
     *
     * Literal-aware rules:
     *
     * INT32 variable + literal 2
     * -> INT32
     *
     * INT32 variable + literal 999999999999
     * -> INT64
     *
     * INT32 variable + INT64 variable
     * -> INT64
     */
    private NexaType common(
            NexaType leftType,
            NexaType rightType,
            SourceSpan span,
            Expr leftExpr,
            Expr rightExpr) {

        leftType = resolve(leftType);
        rightType = resolve(rightType);

        // Exact type.
        if (NexaType.same(
                leftType,
                rightType)) {

            return leftType;
        }

        // -------------------------------------------------------------
        // Literal-aware arithmetic
        // -------------------------------------------------------------

        if (leftExpr instanceof Literal
                && !rightExprIsLiteralOrConstant(
                        rightExpr)) {

            if (literalFits(
                    (Literal) leftExpr,
                    rightType)) {

                return rightType;
            }
        }

        if (rightExpr instanceof Literal
                && !rightExprIsLiteralOrConstant(
                        leftExpr)) {

            if (literalFits(
                    (Literal) rightExpr,
                    leftType)) {

                return leftType;
            }
        }

        // -------------------------------------------------------------
        // Both sides numeric
        // -------------------------------------------------------------

        if (NexaType.numeric(leftType)
                && NexaType.numeric(rightType)) {

            if (leftType.displayName()
                    .equals("FLOAT64")
                    || rightType.displayName()
                            .equals("FLOAT64")) {

                return NexaType.FLOAT64;
            }

            if (leftType.displayName()
                    .equals("FLOAT32")
                    || rightType.displayName()
                            .equals("FLOAT32")) {

                return NexaType.FLOAT32;
            }

            return rank(leftType) >= rank(rightType)
                    ? leftType
                    : rightType;
        }

        bad(
                "No compatible types: "
                        + leftType.displayName()
                        + " and "
                        + rightType.displayName(),
                span);

        return NexaType.OBJECT;
    }

    /**
     * Backwards-compatible helper for array type inference.
     */
    private NexaType common(
            NexaType a,
            NexaType b,
            Expr source,
            NexaType leftExprType,
            NexaType rightExprType) {

        return common(
                a,
                b,
                source.span(),
                source,
                source);
    }

    private boolean rightExprIsLiteralOrConstant(
            Expr expression) {

        return expression instanceof Literal;
    }

    // ========================================================================
    // Literal compatibility
    // ========================================================================

    private boolean literalFits(
            Literal literal,
            NexaType target) {

        target = resolve(target);

        if (!(literal.value() instanceof Number number)) {

            return false;
        }

        if (!NexaType.numeric(target)) {
            return false;
        }

        double value = number.doubleValue();

        if (!Double.isFinite(value)) {
            return false;
        }

        if (requiresWholeNumber(target)
                && !isWhole(value)) {

            return false;
        }

        return switch (target.displayName()) {

            case "INT8" ->
                value >= -128
                        && value <= 127;

            case "INT16" ->
                value >= -32768
                        && value <= 32767;

            case "INT32" ->
                value >= Integer.MIN_VALUE
                        && value <= Integer.MAX_VALUE;

            case "INT64" ->
                true;

            case "UINT8" ->
                value >= 0
                        && value <= 255;

            case "UINT16" ->
                value >= 0
                        && value <= 65535;

            case "UINT32" ->
                value >= 0
                        && value <= 4294967295d;

            case "UINT64" ->
                value >= 0;

            case "FLOAT32",
                    "FLOAT64" ->
                true;

            default ->
                false;
        };
    }

    // ========================================================================
    // Numeric ranking
    // ========================================================================

    private int rank(
            NexaType type) {

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

            case "FLOAT32" ->
                5;

            case "FLOAT64" ->
                6;

            default ->
                0;
        };
    }

    // ========================================================================
    // Type resolution
    // ========================================================================

    private NexaType resolve(
            NexaType type) {

        if (type instanceof NexaType.Named named) {

            NexaType resolved = types.get(named.name());

            if (resolved == null) {

                bad(
                        "Unknown type: "
                                + named.name(),
                        new SourceSpan(0, 0));

                return NexaType.OBJECT;
            }

            return resolve(resolved);
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

            return new NexaType.ObjectType(
                    fields);
        }

        return type;
    }

    // ========================================================================
    // LValues
    // ========================================================================

    private boolean lvalue(
            Expr expression) {

        return expression instanceof Var
                || expression instanceof Field
                || expression instanceof Index;
    }

    // ========================================================================
    // Scope
    // ========================================================================

    private void define(
            String name,
            NexaType type) {

        scopes.peek().put(
                name,
                type);
    }

    private NexaType lookup(
            String name) {

        for (Map<String, NexaType> scope : scopes) {

            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }

        return null;
    }

    // ========================================================================
    // Diagnostics
    // ========================================================================

    private void bad(
            String message,
            SourceSpan span) {

        errors.add(
                new Diagnostic(
                        message,
                        span));
    }
}