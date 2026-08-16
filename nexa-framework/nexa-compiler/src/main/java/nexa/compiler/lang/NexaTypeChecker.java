package nexa.compiler.lang;

import java.util.*;
import static nexa.compiler.lang.NexaAst.*;

public final class NexaTypeChecker {
    public record Diagnostic(String message, SourceSpan span) {}

    private final Map<String, NexaType> types = new LinkedHashMap<>();
    private final List<Diagnostic> errors = new ArrayList<>();
    private final Deque<Map<String, NexaType>> scopes = new ArrayDeque<>();
    private final Set<String> constants = new HashSet<>();

    public NexaTypeChecker() {
        registerBuiltins();
    }

    private void registerBuiltins() {
        types.clear();
        for (NexaType t : List.of(
                NexaType.BOOLEAN, NexaType.INT8, NexaType.INT16, NexaType.INT32, NexaType.INT64,
                NexaType.UINT8, NexaType.UINT16, NexaType.UINT32, NexaType.UINT64,
                NexaType.FLOAT32, NexaType.FLOAT64, NexaType.STRING, NexaType.OBJECT, NexaType.VOID)) {
            types.put(t.displayName(), t);
        }
    }

    public List<Diagnostic> check(Program p) {
        errors.clear();
        scopes.clear();
        constants.clear();
        registerBuiltins();
        scopes.push(new LinkedHashMap<>());
        for (Stmt s : p.statements()) checkStmt(s);
        return List.copyOf(errors);
    }

    private void checkStmt(Stmt s) {
        if (s instanceof TypeDecl d) {
            if (types.containsKey(d.name())) err("Type already defined: " + d.name(), d.span());
            else types.put(d.name(), d.type());
            return;
        }

        if (s instanceof Let l) {
            NexaType declared = resolve(l.type());
            NexaType init = expr(l.init());
            requireAssignable(declared, init, l.init(), l.span(), "initializer");
            if (lookupCurrent(l.name()) != null) err("Variable already defined: " + l.name(), l.span());
            define(l.name(), declared);
            if (l.constant()) constants.add(l.name());
            return;
        }

        if (s instanceof Assign a) {
            NexaType target = expr(a.target());
            NexaType value = expr(a.value());
            if (!isLValue(a.target())) {
                err("Assignment target is not writable", a.target().span());
            } else if (a.target() instanceof Var v && isConstant(v.name())) {
                err("Cannot assign to const variable: " + v.name(), a.span());
            }
            requireAssignable(target, value, a.value(), a.span(), "assignment");
            return;
        }

        if (s instanceof Return r) {
            if (r.value() != null) expr(r.value());
            return;
        }

        if (s instanceof ExprStmt e) {
            expr(e.expr());
            return;
        }

        if (s instanceof For f) {
            NexaType iterable = expr(f.iterable());
            if (!(iterable instanceof NexaType.Array arr)) {
                if (!NexaType.same(iterable, NexaType.OBJECT)) {
                    err("'in' requires ARRAY<T> or OBJECT", f.iterable().span());
                }
            } else {
                requireAssignable(resolve(f.declaredType()), arr.element(), f.iterable(), f.span(), "loop variable");
            }

            scopes.push(new LinkedHashMap<>());
            define(f.name(), resolve(f.declaredType()));
            for (Stmt b : f.body()) checkStmt(b);
            scopes.pop();
        }
    }

    private NexaType expr(Expr e) {
        if (e instanceof Literal l) return resolve(l.type());

        if (e instanceof Var v) {
            NexaType t = lookup(v.name());
            if (t == null) {
                err("Unknown variable: " + v.name(), v.span());
                return NexaType.OBJECT;
            }
            return resolve(t);
        }

        if (e instanceof Field f) {
            NexaType t = resolve(expr(f.target()));
            if (t instanceof NexaType.ObjectType o) {
                NexaType x = o.fields().get(f.name());
                if (x == null) err("Unknown field '" + f.name() + "'", f.span());
                return x == null ? NexaType.OBJECT : resolve(x);
            }
            if (NexaType.same(t, NexaType.OBJECT)) return NexaType.OBJECT;
            err("Field access requires OBJECT/struct", f.span());
            return NexaType.OBJECT;
        }

        if (e instanceof Index i) {
            NexaType t = resolve(expr(i.target()));
            NexaType idx = expr(i.index());
            if (t instanceof NexaType.Array a) {
                requireAssignable(NexaType.INT64, idx, i.index(), i.index().span(), "array index");
                return resolve(a.element());
            }
            if (NexaType.same(t, NexaType.OBJECT)) return NexaType.OBJECT;
            err("Indexing requires ARRAY or OBJECT", i.span());
            return NexaType.OBJECT;
        }

        if (e instanceof Array a) {
            NexaType element = null;
            for (Expr x : a.values()) {
                NexaType xt = expr(x);
                element = element == null ? xt : common(element, xt, a.span());
            }
            return new NexaType.Array(element == null ? NexaType.OBJECT : element);
        }

        if (e instanceof ObjectLit o) {
            Map<String, NexaType> fields = new LinkedHashMap<>();
            for (var x : o.fields().entrySet()) fields.put(x.getKey(), expr(x.getValue()));
            return new NexaType.ObjectType(fields);
        }

        if (e instanceof Unary u) {
            NexaType t = expr(u.expr());
            if (u.op().equals("!")) {
                requireAssignable(NexaType.BOOLEAN, t, u.expr(), u.span(), "logical negation");
                return NexaType.BOOLEAN;
            }
            if (!NexaType.numeric(t)) err("Unary numeric operator requires numeric value", u.span());
            return t;
        }

        if (e instanceof Binary b) {
            NexaType l = expr(b.left());
            NexaType r = expr(b.right());
            if (List.of("&&", "||").contains(b.op())) {
                requireAssignable(NexaType.BOOLEAN, l, b.left(), b.left().span(), "logical operand");
                requireAssignable(NexaType.BOOLEAN, r, b.right(), b.right().span(), "logical operand");
                return NexaType.BOOLEAN;
            }
            if (List.of("==", "!=", "<", "<=", ">", ">=").contains(b.op())) {
                if (!compatible(l, r)) err("Incompatible comparison types: " + l.displayName() + " and " + r.displayName(), b.span());
                return NexaType.BOOLEAN;
            }
            if (!NexaType.numeric(l) || !NexaType.numeric(r)) err("Arithmetic requires numeric operands", b.span());
            return common(l, r, b.span());
        }

        if (e instanceof Call c) {
            for (Expr a : c.args()) expr(a);
            return NexaType.OBJECT;
        }

        throw new IllegalStateException();
    }

    private NexaType common(NexaType a, NexaType b, SourceSpan s) {
        if (NexaType.same(a, b)) return a;
        if (NexaType.same(a, NexaType.OBJECT) || NexaType.same(b, NexaType.OBJECT)) return NexaType.OBJECT;
        if (NexaType.numeric(a) && NexaType.numeric(b)) {
            if (a.displayName().startsWith("FLOAT") || b.displayName().startsWith("FLOAT")) return NexaType.FLOAT64;
            if (a.displayName().contains("64") || b.displayName().contains("64")) return NexaType.INT64;
            return NexaType.INT32;
        }
        err("No safe common type for " + a.displayName() + " and " + b.displayName(), s);
        return NexaType.OBJECT;
    }

    private boolean compatible(NexaType a, NexaType b) {
        return NexaType.same(a, b)
                || (NexaType.numeric(a) && NexaType.numeric(b))
                || NexaType.same(a, NexaType.OBJECT)
                || NexaType.same(b, NexaType.OBJECT);
    }

    private void requireAssignable(
            NexaType target,
            NexaType value,
            Expr sourceExpr,
            SourceSpan s,
            String where) {
        target = resolve(target);
        value = resolve(value);

        if (NexaType.same(target, value)) return;
        // OBJECT is Nexa's dynamic boundary: plugin/host values and dynamic
        // object members may cross it without compile-time schema knowledge.
        if (NexaType.same(target, NexaType.OBJECT)) return;
        if (NexaType.same(value, NexaType.OBJECT)) return;

        if (target instanceof NexaType.Array ta && value instanceof NexaType.Array va) {
            if (assignable(ta.element(), va.element())) return;
        }

        if (target instanceof NexaType.ObjectType to && value instanceof NexaType.ObjectType from) {
            if (assignableObject(to, from)) return;
        }

        if (NexaType.numeric(target) && NexaType.numeric(value)) {
            if (rank(value) <= rank(target)) return;
            // Integer literals are represented as INT64 only when they do not
            // fit INT32; all INT32 literals are already context-safe.
            if (sourceExpr instanceof Literal literal && literal.value() instanceof Number n
                    && isRepresentableIntegerLiteral(n, target)) return;
        }

        err("Type mismatch in " + where + ": expected " + target.displayName() + ", got " + value.displayName(), s);
    }

    private boolean assignable(NexaType expected, NexaType actual) {
        expected = resolve(expected);
        actual = resolve(actual);
        if (NexaType.same(expected, actual)) return true;
        if (NexaType.same(expected, NexaType.OBJECT) || NexaType.same(actual, NexaType.OBJECT)) return true;
        if (NexaType.numeric(expected) && NexaType.numeric(actual)) return rank(actual) <= rank(expected);
        if (expected instanceof NexaType.Array ea && actual instanceof NexaType.Array aa) return assignable(ea.element(), aa.element());
        if (expected instanceof NexaType.ObjectType eo && actual instanceof NexaType.ObjectType ao) return assignableObject(eo, ao);
        return false;
    }

    private boolean assignableObject(NexaType.ObjectType expected, NexaType.ObjectType actual) {
        if (!expected.fields().keySet().equals(actual.fields().keySet())) return false;
        for (String name : expected.fields().keySet()) {
            if (!assignable(expected.fields().get(name), actual.fields().get(name))) return false;
        }
        return true;
    }

    private boolean isRepresentableIntegerLiteral(Number n, NexaType target) {
        if (target.displayName().startsWith("FLOAT")) return true;
        long value = n.longValue();
        return switch (target.displayName()) {
            case "INT8" -> value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
            case "INT16" -> value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
            case "INT32" -> value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
            case "INT64" -> true;
            case "UINT8" -> value >= 0 && value <= 255;
            case "UINT16" -> value >= 0 && value <= 65535;
            case "UINT32" -> value >= 0 && value <= 0xffffffffL;
            case "UINT64" -> value >= 0;
            default -> false;
        };
    }

    private int rank(NexaType t) {
        return switch (t.displayName()) {
            case "INT8", "UINT8" -> 1;
            case "INT16", "UINT16" -> 2;
            case "INT32", "UINT32" -> 3;
            case "INT64", "UINT64" -> 4;
            case "FLOAT32" -> 5;
            case "FLOAT64" -> 6;
            default -> 0;
        };
    }

    private NexaType resolve(NexaType t) {
        if (t instanceof NexaType.Named n) {
            NexaType r = types.get(n.name());
            if (r == null) {
                err("Unknown type: " + n.name(), new SourceSpan(0, 0));
                return NexaType.OBJECT;
            }
            return resolve(r);
        }
        if (t instanceof NexaType.Array a) return new NexaType.Array(resolve(a.element()));
        if (t instanceof NexaType.ObjectType o) {
            Map<String, NexaType> fields = new LinkedHashMap<>();
            o.fields().forEach((name, type) -> fields.put(name, resolve(type)));
            return new NexaType.ObjectType(fields);
        }
        return t;
    }

    private boolean isLValue(Expr e) { return e instanceof Var || e instanceof Field || e instanceof Index; }
    private void define(String n, NexaType t) { scopes.peek().put(n, t); }
    private NexaType lookup(String n) { for (var s : scopes) if (s.containsKey(n)) return s.get(n); return null; }
    private NexaType lookupCurrent(String n) { return scopes.peek().get(n); }
    private boolean isConstant(String n) { return constants.contains(n); }
    private void err(String m, SourceSpan s) { errors.add(new Diagnostic(m, s)); }
}