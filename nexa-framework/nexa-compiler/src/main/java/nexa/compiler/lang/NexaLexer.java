package nexa.compiler.lang;

import java.util.*;

public final class NexaLexer {
    private static final Map<String, NexaToken.Kind> WORDS = Map.ofEntries(
        Map.entry("let", NexaToken.Kind.LET), Map.entry("const", NexaToken.Kind.CONST), Map.entry("type", NexaToken.Kind.TYPE),
        Map.entry("return", NexaToken.Kind.RETURN), Map.entry("for", NexaToken.Kind.FOR), Map.entry("in", NexaToken.Kind.IN),
        Map.entry("true", NexaToken.Kind.TRUE), Map.entry("false", NexaToken.Kind.FALSE),
        Map.entry("BOOLEAN", NexaToken.Kind.BOOLEAN), Map.entry("INT8", NexaToken.Kind.INT8), Map.entry("INT16", NexaToken.Kind.INT16),
        Map.entry("INT32", NexaToken.Kind.INT32), Map.entry("INT64", NexaToken.Kind.INT64), Map.entry("UINT8", NexaToken.Kind.UINT8),
        Map.entry("UINT16", NexaToken.Kind.UINT16), Map.entry("UINT32", NexaToken.Kind.UINT32), Map.entry("UINT64", NexaToken.Kind.UINT64),
        Map.entry("FLOAT32", NexaToken.Kind.FLOAT32), Map.entry("FLOAT64", NexaToken.Kind.FLOAT64), Map.entry("STRING", NexaToken.Kind.STRING_TYPE),
        Map.entry("ARRAY", NexaToken.Kind.ARRAY), Map.entry("OBJECT", NexaToken.Kind.OBJECT));

    private final String s;
    private int p;

    public NexaLexer(String source) { this.s = Objects.requireNonNull(source); }

    public List<NexaToken> lex() {
        List<NexaToken> out = new ArrayList<>();
        while (true) {
            skip();
            if (p >= s.length()) { out.add(t(NexaToken.Kind.EOF, p, p)); return out; }
            int st = p;
            char c = s.charAt(p++);
            switch (c) {
                case '{' -> out.add(t(NexaToken.Kind.LBRACE, st, p)); case '}' -> out.add(t(NexaToken.Kind.RBRACE, st, p));
                case '[' -> out.add(t(NexaToken.Kind.LBRACKET, st, p)); case ']' -> out.add(t(NexaToken.Kind.RBRACKET, st, p));
                case '(' -> out.add(t(NexaToken.Kind.LPAREN, st, p)); case ')' -> out.add(t(NexaToken.Kind.RPAREN, st, p));
                case ':' -> out.add(t(NexaToken.Kind.COLON, st, p)); case ',' -> out.add(t(NexaToken.Kind.COMMA, st, p));
                case ';' -> out.add(t(NexaToken.Kind.SEMICOLON, st, p)); case '.' -> out.add(t(NexaToken.Kind.DOT, st, p));
                case '+' -> out.add(t(NexaToken.Kind.PLUS, st, p)); case '-' -> out.add(t(NexaToken.Kind.MINUS, st, p));
                case '*' -> out.add(t(NexaToken.Kind.STAR, st, p)); case '/' -> out.add(t(NexaToken.Kind.SLASH, st, p));
                case '!' -> out.add(t(match('=') ? NexaToken.Kind.NE : NexaToken.Kind.BANG, st, p));
                case '=' -> out.add(t(match('=') ? NexaToken.Kind.EQEQ : NexaToken.Kind.EQ, st, p));
                case '<' -> out.add(t(match('=') ? NexaToken.Kind.LE : NexaToken.Kind.LT, st, p));
                case '>' -> out.add(t(match('=') ? NexaToken.Kind.GE : NexaToken.Kind.GT, st, p));
                case '&' -> { if (!match('&')) throw err("Expected &"); out.add(t(NexaToken.Kind.AND, st, p)); }
                case '|' -> { if (!match('|')) throw err("Expected |"); out.add(t(NexaToken.Kind.OR, st, p)); }
                case '"' -> out.add(string(st));
                default -> { if (Character.isLetter(c) || c == '_') out.add(word(st)); else if (Character.isDigit(c)) out.add(number(st)); else throw err("Unexpected character '" + c + "'"); }
            }
        }
    }

    private void skip() { while (p < s.length()) { char c = s.charAt(p); if (Character.isWhitespace(c)) { p++; continue; } if (c == '/' && p + 1 < s.length() && s.charAt(p + 1) == '/') { p += 2; while (p < s.length() && s.charAt(p) != '\n') p++; continue; } break; } }
    private boolean match(char c) { if (p < s.length() && s.charAt(p) == c) { p++; return true; } return false; }
    private NexaToken word(int st) { while (p < s.length() && (Character.isLetterOrDigit(s.charAt(p)) || s.charAt(p) == '_')) p++; String x = s.substring(st, p); return new NexaToken(WORDS.getOrDefault(x, NexaToken.Kind.IDENT), x, new SourceSpan(st, p)); }
    private NexaToken number(int st) { while (p < s.length() && Character.isDigit(s.charAt(p))) p++; NexaToken.Kind k = NexaToken.Kind.INT; if (p < s.length() && s.charAt(p) == '.' && p + 1 < s.length() && Character.isDigit(s.charAt(p + 1))) { k = NexaToken.Kind.FLOAT; p++; while (p < s.length() && Character.isDigit(s.charAt(p))) p++; } return new NexaToken(k, s.substring(st, p), new SourceSpan(st, p)); }
    private NexaToken string(int st) { StringBuilder b = new StringBuilder(); while (p < s.length()) { char c = s.charAt(p++); if (c == '"') return new NexaToken(NexaToken.Kind.STRING, b.toString(), new SourceSpan(st, p)); if (c == '\\' && p < s.length()) { char e = s.charAt(p++); b.append(switch (e) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; case '"' -> '"'; case '\\' -> '\\'; default -> e; }); } else b.append(c); } throw err("Unterminated string"); }
    private NexaToken t(NexaToken.Kind k, int a, int b) { return new NexaToken(k, s.substring(a, b), new SourceSpan(a, b)); }
    private IllegalArgumentException err(String m) { return new IllegalArgumentException(m + " at " + p); }
}
