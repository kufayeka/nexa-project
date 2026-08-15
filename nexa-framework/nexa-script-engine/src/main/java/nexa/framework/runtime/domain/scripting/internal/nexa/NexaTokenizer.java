package nexa.framework.runtime.domain.scripting.internal.nexa;

import java.util.ArrayList;
import java.util.List;

public final class NexaTokenizer {

    private final String source;
    private final List<NexaToken> tokens;
    private int index;
    private int line;
    private int column;

    public NexaTokenizer(String source) {
        this.source = source == null ? "" : source;
        this.tokens = new ArrayList<>();
        this.line = 1;
        this.column = 1;
    }

    public List<NexaToken> tokenize() {
        while (!isAtEnd()) {
            char current = peek();
            int tokenLine = line;
            int tokenColumn = column;

            if (current == ' ' || current == '\t' || current == '\r') {
                advance();
                continue;
            }

            if (current == '\n') {
                advance();
                tokens.add(new NexaToken(NexaTokenType.NEWLINE, "\n", tokenLine, tokenColumn));
                continue;
            }

            if (current == '/' && peekNext() == '/') {
                skipLineComment();
                continue;
            }

            if (current == '/' && peekNext() == '*') {
                skipBlockComment();
                continue;
            }

            if (Character.isJavaIdentifierStart(current)) {
                readIdentifier(tokenLine, tokenColumn);
                continue;
            }

            if (Character.isDigit(current)) {
                readNumber(tokenLine, tokenColumn);
                continue;
            }

            switch (current) {
                case '"' -> readQuotedString('"', tokenLine, tokenColumn);
                case '\'' -> readQuotedString('\'', tokenLine, tokenColumn);
                case '`' -> readTemplate(tokenLine, tokenColumn);
                case '(' -> addSingle(NexaTokenType.LPAREN, tokenLine, tokenColumn);
                case ')' -> addSingle(NexaTokenType.RPAREN, tokenLine, tokenColumn);
                case '{' -> addSingle(NexaTokenType.LBRACE, tokenLine, tokenColumn);
                case '}' -> addSingle(NexaTokenType.RBRACE, tokenLine, tokenColumn);
                case '[' -> addSingle(NexaTokenType.LBRACKET, tokenLine, tokenColumn);
                case ']' -> addSingle(NexaTokenType.RBRACKET, tokenLine, tokenColumn);
                case ',' -> addSingle(NexaTokenType.COMMA, tokenLine, tokenColumn);
                case ':' -> addSingle(NexaTokenType.COLON, tokenLine, tokenColumn);
                case ';' -> addSingle(NexaTokenType.SEMICOLON, tokenLine, tokenColumn);
                case '.' -> addSingle(NexaTokenType.DOT, tokenLine, tokenColumn);
                case '+' -> addConditional('=', NexaTokenType.PLUS_EQUAL, NexaTokenType.PLUS, tokenLine, tokenColumn);
                case '-' -> addConditional('=', NexaTokenType.MINUS_EQUAL, NexaTokenType.MINUS, tokenLine, tokenColumn);
                case '*' -> addConditional('=', NexaTokenType.STAR_EQUAL, NexaTokenType.STAR, tokenLine, tokenColumn);
                case '%' -> addSingle(NexaTokenType.PERCENT, tokenLine, tokenColumn);
                case '!' -> addConditional('=', NexaTokenType.BANG_EQUAL, NexaTokenType.BANG, tokenLine, tokenColumn);
                case '=' -> readEqualsOperator(tokenLine, tokenColumn);
                case '>' -> addConditional('=', NexaTokenType.GREATER_EQUAL, NexaTokenType.GREATER, tokenLine, tokenColumn);
                case '<' -> addConditional('=', NexaTokenType.LESS_EQUAL, NexaTokenType.LESS, tokenLine, tokenColumn);
                case '&' -> addDouble('&', NexaTokenType.AMPERSAND_AMPERSAND, tokenLine, tokenColumn);
                case '|' -> addDouble('|', NexaTokenType.PIPE_PIPE, tokenLine, tokenColumn);
                case '?' -> readQuestionOperator(tokenLine, tokenColumn);
                case '/' -> addConditional('=', NexaTokenType.SLASH_EQUAL, NexaTokenType.SLASH, tokenLine, tokenColumn);
                default -> throw new NexaScriptException(
                        "Karakter tidak didukung: " + current,
                        tokenLine,
                        tokenColumn);
            }
        }

        tokens.add(new NexaToken(NexaTokenType.EOF, "", line, column));
        return List.copyOf(tokens);
    }

    private void readIdentifier(int tokenLine, int tokenColumn) {
        StringBuilder builder = new StringBuilder();
        while (!isAtEnd() && Character.isJavaIdentifierPart(peek())) {
            builder.append(advance());
        }

        String text = builder.toString();
        NexaTokenType type = switch (text) {
            case "true" -> NexaTokenType.TRUE;
            case "false" -> NexaTokenType.FALSE;
            case "null" -> NexaTokenType.NULL;
            case "val" -> NexaTokenType.VAL;
            case "var" -> NexaTokenType.VAR;
            case "fun" -> NexaTokenType.FUNCTION;
            case "if" -> NexaTokenType.IF;
            case "else" -> NexaTokenType.ELSE;
            case "switch" -> NexaTokenType.SWITCH;
            case "case" -> NexaTokenType.CASE;
            case "default" -> NexaTokenType.DEFAULT;
            case "for" -> NexaTokenType.FOR;
            case "return" -> NexaTokenType.RETURN;
            case "in" -> NexaTokenType.IN;
            default -> NexaTokenType.IDENTIFIER;
        };
        tokens.add(new NexaToken(type, text, tokenLine, tokenColumn));
    }

    private void readNumber(int tokenLine, int tokenColumn) {
        StringBuilder builder = new StringBuilder();
        while (!isAtEnd() && Character.isDigit(peek())) {
            builder.append(advance());
        }

        if (!isAtEnd() && peek() == '.' && Character.isDigit(peekNext())) {
            builder.append(advance());
            while (!isAtEnd() && Character.isDigit(peek())) {
                builder.append(advance());
            }
        }

        tokens.add(new NexaToken(NexaTokenType.NUMBER, builder.toString(), tokenLine, tokenColumn));
    }

    private void readQuotedString(char quote, int tokenLine, int tokenColumn) {
        advance();
        StringBuilder builder = new StringBuilder();
        while (!isAtEnd() && peek() != quote) {
            char current = advance();
            if (current == '\\') {
                builder.append(readEscape(tokenLine, tokenColumn));
                continue;
            }
            builder.append(current);
        }

        if (isAtEnd()) {
            throw new NexaScriptException("String tidak ditutup", tokenLine, tokenColumn);
        }

        advance();
        tokens.add(new NexaToken(NexaTokenType.STRING, builder.toString(), tokenLine, tokenColumn));
    }

    private void readTemplate(int tokenLine, int tokenColumn) {
        advance();
        StringBuilder builder = new StringBuilder();
        while (!isAtEnd() && peek() != '`') {
            char current = advance();
            if (current == '\\') {
                builder.append(readEscape(tokenLine, tokenColumn));
                continue;
            }
            builder.append(current);
        }

        if (isAtEnd()) {
            throw new NexaScriptException("Template string tidak ditutup", tokenLine, tokenColumn);
        }

        advance();
        tokens.add(new NexaToken(NexaTokenType.TEMPLATE, builder.toString(), tokenLine, tokenColumn));
    }

    private char readEscape(int tokenLine, int tokenColumn) {
        if (isAtEnd()) {
            throw new NexaScriptException("Escape sequence tidak lengkap", tokenLine, tokenColumn);
        }

        char escaped = advance();
        return switch (escaped) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '\\' -> '\\';
            case '"' -> '"';
            case '\'' -> '\'';
            case '`' -> '`';
            default -> escaped;
        };
    }

    private void readQuestionOperator(int tokenLine, int tokenColumn) {
        advance();
        if (match('.')) {
            tokens.add(new NexaToken(NexaTokenType.SAFE_DOT, "?.", tokenLine, tokenColumn));
            return;
        }

        if (match('?')) {
            tokens.add(new NexaToken(NexaTokenType.QUESTION_QUESTION, "??", tokenLine, tokenColumn));
            return;
        }

        throw new NexaScriptException("Operator ? tidak didukung", tokenLine, tokenColumn);
    }

    private void addSingle(NexaTokenType type, int tokenLine, int tokenColumn) {
        char text = advance();
        tokens.add(new NexaToken(type, String.valueOf(text), tokenLine, tokenColumn));
    }

    private void addConditional(
            char expected,
            NexaTokenType matched,
            NexaTokenType plain,
            int tokenLine,
            int tokenColumn) {
        char current = advance();
        if (match(expected)) {
            tokens.add(new NexaToken(matched, "" + current + expected, tokenLine, tokenColumn));
            return;
        }
        tokens.add(new NexaToken(plain, String.valueOf(current), tokenLine, tokenColumn));
    }

    private void readEqualsOperator(int tokenLine, int tokenColumn) {
        advance();
        if (match('>')) {
            tokens.add(new NexaToken(NexaTokenType.ARROW, "=>", tokenLine, tokenColumn));
            return;
        }
        if (match('=')) {
            tokens.add(new NexaToken(NexaTokenType.EQUAL_EQUAL, "==", tokenLine, tokenColumn));
            return;
        }
        tokens.add(new NexaToken(NexaTokenType.EQUAL, "=", tokenLine, tokenColumn));
    }

    private void addDouble(char expected, NexaTokenType type, int tokenLine, int tokenColumn) {
        char current = advance();
        if (!match(expected)) {
            throw new NexaScriptException("Operator tidak didukung: " + current, tokenLine, tokenColumn);
        }
        tokens.add(new NexaToken(type, "" + current + expected, tokenLine, tokenColumn));
    }

    private void skipLineComment() {
        while (!isAtEnd() && peek() != '\n') {
            advance();
        }
    }

    private void skipBlockComment() {
        advance();
        advance();
        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') {
                advance();
                advance();
                return;
            }
            advance();
        }
    }

    private boolean match(char expected) {
        if (isAtEnd() || peek() != expected) {
            return false;
        }
        advance();
        return true;
    }

    private char peek() {
        return source.charAt(index);
    }

    private char peekNext() {
        return index + 1 >= source.length() ? '\0' : source.charAt(index + 1);
    }

    private char advance() {
        char current = source.charAt(index++);
        if (current == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return current;
    }

    private boolean isAtEnd() {
        return index >= source.length();
    }
}


