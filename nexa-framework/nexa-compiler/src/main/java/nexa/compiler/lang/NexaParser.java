package nexa.compiler.lang;

import java.util.*;
import nexa.compiler.lang.NexaAst.*;

public final class NexaParser {
    private final List<NexaToken> ts;
    private int p;

    public NexaParser(List<NexaToken> tokens) { ts = List.copyOf(tokens); }

    public Program parse() {
        List<Stmt> s = new ArrayList<>();
        while (!at(NexaToken.Kind.EOF)) s.add(stmt());
        return new Program(s);
    }

    private Stmt stmt() {
        if (match(NexaToken.Kind.TYPE)) return typeDecl();
        if (match(NexaToken.Kind.LET)) return declaration(false);
        if (match(NexaToken.Kind.CONST)) return declaration(true);
        if (match(NexaToken.Kind.RETURN)) {
            int st = prev().span().start(); Expr e = expr(); semi();
            return new Return(e, span(st, e.span().end()));
        }
        if (match(NexaToken.Kind.FOR)) return forStmt();
        Expr a = expr();
        if (match(NexaToken.Kind.EQ)) { Expr v = expr(); semi(); return new Assign(a, v, span(a.span().start(), v.span().end())); }
        semi();
        return new ExprStmt(a, a.span());
    }

    private Stmt typeDecl() {
        int st = prev().span().start(); String n = expect(NexaToken.Kind.IDENT).text(); expect(NexaToken.Kind.EQ);
        NexaType.ObjectType t = (NexaType.ObjectType) type(); semi();
        return new TypeDecl(n, t, span(st, tok(-1).span().end()));
    }

    private Stmt declaration(boolean constant) {
        int st = prev().span().start();
        String n = expect(NexaToken.Kind.IDENT).text();
        expect(NexaToken.Kind.COLON);
        NexaType t = type();
        expect(NexaToken.Kind.EQ);
        Expr e = expr();
        semi();
        return new Let(n, t, e, constant, span(st, e.span().end()));
    }

    private Stmt forStmt() {
        int st = prev().span().start();
        expect(NexaToken.Kind.LPAREN);
        match(NexaToken.Kind.LET);
        String n = expect(NexaToken.Kind.IDENT).text();
        expect(NexaToken.Kind.COLON);
        NexaType t = type();
        expect(NexaToken.Kind.IN);
        Expr it = expr();
        expect(NexaToken.Kind.RPAREN);
        expect(NexaToken.Kind.LBRACE);
        List<Stmt> b = new ArrayList<>();
        while (!at(NexaToken.Kind.RBRACE)) b.add(stmt());
        NexaToken r = expect(NexaToken.Kind.RBRACE);
        return new For(n, t, it, b, span(st, r.span().end()));
    }

    private NexaType type() {
        NexaToken t = tok(0);
        if (match(NexaToken.Kind.ARRAY)) { expect(NexaToken.Kind.LT); NexaType e = type(); expect(NexaToken.Kind.GT); return new NexaType.Array(e); }
        if (match(NexaToken.Kind.LBRACE)) {
            Map<String, NexaType> f = new LinkedHashMap<>();
            while (!at(NexaToken.Kind.RBRACE)) {
                String n = expect(NexaToken.Kind.IDENT).text(); expect(NexaToken.Kind.COLON); f.put(n, type());
                if (!match(NexaToken.Kind.COMMA)) break;
            }
            expect(NexaToken.Kind.RBRACE); return new NexaType.ObjectType(f);
        }
        p++; return primitiveOrNamed(t);
    }

    private NexaType primitiveOrNamed(NexaToken t) {
        return switch (t.kind()) {
            case BOOLEAN -> NexaType.BOOLEAN; case INT8 -> NexaType.INT8; case INT16 -> NexaType.INT16; case INT32 -> NexaType.INT32; case INT64 -> NexaType.INT64;
            case UINT8 -> NexaType.UINT8; case UINT16 -> NexaType.UINT16; case UINT32 -> NexaType.UINT32; case UINT64 -> NexaType.UINT64;
            case FLOAT32 -> NexaType.FLOAT32; case FLOAT64 -> NexaType.FLOAT64; case STRING_TYPE -> NexaType.STRING; case OBJECT -> NexaType.OBJECT;
            case IDENT -> new NexaType.Named(t.text(), null); default -> throw error("Expected type");
        };
    }

    private Expr expr() { return binary(0); }
    private static final Map<NexaToken.Kind,Integer> PREC = Map.ofEntries(
        Map.entry(NexaToken.Kind.OR,1), Map.entry(NexaToken.Kind.AND,2), Map.entry(NexaToken.Kind.EQEQ,3), Map.entry(NexaToken.Kind.NE,3),
        Map.entry(NexaToken.Kind.LT,4), Map.entry(NexaToken.Kind.LE,4), Map.entry(NexaToken.Kind.GT,4), Map.entry(NexaToken.Kind.GE,4),
        Map.entry(NexaToken.Kind.PLUS,5), Map.entry(NexaToken.Kind.MINUS,5), Map.entry(NexaToken.Kind.STAR,6), Map.entry(NexaToken.Kind.SLASH,6));

    private Expr binary(int min) {
        Expr l = unary();
        while (true) { Integer q = PREC.get(tok(0).kind()); if (q == null || q < min) break; NexaToken op = next(); Expr r = binary(q + 1); l = new Binary(op.text(), l, r, span(l.span().start(), r.span().end())); }
        return l;
    }

    private Expr unary() {
        if (match(NexaToken.Kind.BANG) || match(NexaToken.Kind.MINUS) || match(NexaToken.Kind.PLUS)) { NexaToken o = prev(); Expr e = unary(); return new Unary(o.text(), e, span(o.span().start(), e.span().end())); }
        return postfix();
    }

    private Expr postfix() {
        Expr e = primary();
        while (true) {
            if (match(NexaToken.Kind.DOT)) { String n = expect(NexaToken.Kind.IDENT).text(); e = new Field(e, n, span(e.span().start(), prev().span().end())); }
            else if (match(NexaToken.Kind.LBRACKET)) { Expr i = expr(); NexaToken r = expect(NexaToken.Kind.RBRACKET); e = new Index(e, i, span(e.span().start(), r.span().end())); }
            else if (match(NexaToken.Kind.LPAREN)) {
                List<Expr> a = new ArrayList<>(); if (!at(NexaToken.Kind.RPAREN)) { do { a.add(expr()); } while (match(NexaToken.Kind.COMMA)); }
                NexaToken r = expect(NexaToken.Kind.RPAREN); e = new Call(e, a, span(e.span().start(), r.span().end()));
            } else break;
        }
        return e;
    }

    private Expr primary() {
        NexaToken t = next();
        return switch (t.kind()) {
            // Unsuffixed integer literals use INT32 as Nexa's default integer type.
            // Store the lexical integer as long so literals wider than Java int can
            // still reach the semantic range checker without changing their Nexa type.
            case INT -> new Literal(Long.valueOf(t.text()), NexaType.INT32, t.span());
            case FLOAT -> new Literal(Double.parseDouble(t.text()), NexaType.FLOAT64, t.span());
            case STRING -> new Literal(t.text(), NexaType.STRING, t.span());
            case TRUE -> new Literal(true, NexaType.BOOLEAN, t.span()); case FALSE -> new Literal(false, NexaType.BOOLEAN, t.span());
            case IDENT -> new Var(t.text(), t.span()); case LBRACKET -> array(t); case LBRACE -> object(t);
            case LPAREN -> { Expr e = expr(); expect(NexaToken.Kind.RPAREN); yield e; }
            default -> throw error("Expected expression");
        };
    }

    private Expr array(NexaToken l) {
        List<Expr> a = new ArrayList<>(); if (!at(NexaToken.Kind.RBRACKET)) { do { a.add(expr()); } while (match(NexaToken.Kind.COMMA)); }
        NexaToken r = expect(NexaToken.Kind.RBRACKET); return new Array(a, span(l.span().start(), r.span().end()));
    }

    private Expr object(NexaToken l) {
        Map<String, Expr> m = new LinkedHashMap<>();
        if (!at(NexaToken.Kind.RBRACE)) { do { String k = expect(NexaToken.Kind.IDENT).text(); expect(NexaToken.Kind.COLON); m.put(k, expr()); } while (match(NexaToken.Kind.COMMA)); }
        NexaToken r = expect(NexaToken.Kind.RBRACE); return new ObjectLit(m, span(l.span().start(), r.span().end()));
    }

    private void semi() { if (!match(NexaToken.Kind.SEMICOLON)) throw error("Expected ';'"); }
    private boolean match(NexaToken.Kind k) { if (at(k)) { p++; return true; } return false; }
    private boolean at(NexaToken.Kind k) { return tok(0).kind() == k; }
    private NexaToken next() { return ts.get(p++); }
    private NexaToken prev() { return ts.get(p - 1); }
    private NexaToken tok(int d) { int i = Math.min(Math.max(p + d, 0), ts.size() - 1); return ts.get(i); }
    private NexaToken expect(NexaToken.Kind k) { if (!at(k)) throw error("Expected " + k + ", got " + tok(0).kind()); return next(); }
    private IllegalArgumentException error(String m) { return new IllegalArgumentException(m + " at " + tok(0).span().start()); }
    private SourceSpan span(int a, int b) { return new SourceSpan(a, b); }
}