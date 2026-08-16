package nexa.compiler.lang;

import java.util.*;

public final class NexaAst {
    private NexaAst() {}

    public sealed interface Stmt permits Let, Assign, Return, TypeDecl, ExprStmt, For {
        SourceSpan span();
    }

    public record Program(List<Stmt> statements) {
        public Program { statements = List.copyOf(statements); }
    }

    /** Variable declaration. A const declaration has constant=true. */
    public record Let(String name, NexaType type, Expr init, boolean constant, SourceSpan span) implements Stmt {}

    public record Assign(Expr target, Expr value, SourceSpan span) implements Stmt {}
    public record Return(Expr value, SourceSpan span) implements Stmt {}
    public record TypeDecl(String name, NexaType.ObjectType type, SourceSpan span) implements Stmt {}
    public record ExprStmt(Expr expr, SourceSpan span) implements Stmt {}
    public record For(String name, NexaType declaredType, Expr iterable, List<Stmt> body, SourceSpan span) implements Stmt {
        public For { body = List.copyOf(body); }
    }

    public sealed interface Expr permits Literal, Var, Unary, Binary, Field, Index, Array, ObjectLit, Call {
        SourceSpan span();
    }

    public record Literal(Object value, NexaType type, SourceSpan span) implements Expr {}
    public record Var(String name, SourceSpan span) implements Expr {}
    public record Unary(String op, Expr expr, SourceSpan span) implements Expr {}
    public record Binary(String op, Expr left, Expr right, SourceSpan span) implements Expr {}
    public record Field(Expr target, String name, SourceSpan span) implements Expr {}
    public record Index(Expr target, Expr index, SourceSpan span) implements Expr {}
    public record Array(List<Expr> values, SourceSpan span) implements Expr {
        public Array { values = List.copyOf(values); }
    }
    public record ObjectLit(Map<String, Expr> fields, SourceSpan span) implements Expr {
        public ObjectLit { fields = Map.copyOf(fields); }
    }
    public record Call(Expr target, List<Expr> args, SourceSpan span) implements Expr {
        public Call { args = List.copyOf(args); }
    }
}
