package nexa.framework.runtime.domain.scripting.internal.nexa;

import java.util.ArrayList;
import java.util.List;

public final class NexaParser {

    private final List<NexaToken> tokens;
    private int current;

    public NexaParser(List<NexaToken> tokens) {
        this.tokens = tokens;
    }

    public NexaProgram parseProgram() {
        List<NexaStatement> statements = new ArrayList<>();
        skipTerminators();
        while (!isAtEnd()) {
            statements.add(parseStatement());
            skipTerminators();
        }
        return new NexaProgram(List.copyOf(statements));
    }

    public NexaExpression parseExpressionOnly() {
        NexaExpression expression = parseExpression();
        skipTerminators();
        if (!isAtEnd()) {
            NexaToken token = peek();
            throw new NexaScriptException("Token tidak terduga: " + token.text(), token.line(), token.column());
        }
        return expression;
    }

    private NexaStatement parseStatement() {
        if (match(NexaTokenType.VAL)) {
            return parseVariableDeclaration(true);
        }
        if (match(NexaTokenType.VAR)) {
            return parseVariableDeclaration(false);
        }
        if (check(NexaTokenType.FUNCTION) && checkNext(NexaTokenType.IDENTIFIER)) {
            advance();
            return parseFunctionDeclaration();
        }
        if (match(NexaTokenType.IF)) {
            return parseIfStatement();
        }
        if (match(NexaTokenType.SWITCH)) {
            return parseSwitchStatement();
        }
        if (match(NexaTokenType.FOR)) {
            return parseForStatement();
        }
        if (match(NexaTokenType.RETURN)) {
            return parseReturnStatement();
        }
        if (match(NexaTokenType.LBRACE)) {
            return parseBlockStatement();
        }
        return new NexaExpressionStatement(parseExpression());
    }

    private NexaStatement parseVariableDeclaration(boolean readOnly) {
        NexaToken name = consume(NexaTokenType.IDENTIFIER, "Nama variabel wajib diisi");
        NexaExpression initializer = null;
        if (match(NexaTokenType.EQUAL)) {
            initializer = parseExpression();
        }
        return new NexaVariableDeclaration(readOnly, name.text(), initializer, name.line(), name.column());
    }

    private NexaStatement parseFunctionDeclaration() {
        NexaToken name = consume(NexaTokenType.IDENTIFIER, "Nama function wajib diisi");
        List<String> parameters = parseFunctionParameters();
        NexaBlockStatement body = parseFunctionBody();
        return new NexaFunctionDeclaration(name.text(), parameters, body, name.line(), name.column());
    }

    private NexaStatement parseIfStatement() {
        consume(NexaTokenType.LPAREN, "If wajib memakai tanda (");
        NexaExpression condition = parseExpression();
        consume(NexaTokenType.RPAREN, "If wajib memakai tanda )");
        NexaStatement thenBranch = parseBranchStatement();

        NexaStatement elseBranch = null;
        if (match(NexaTokenType.ELSE)) {
            if (match(NexaTokenType.IF)) {
                elseBranch = parseIfStatement();
            } else {
                elseBranch = parseBranchStatement();
            }
        }

        return new NexaIfStatement(condition, thenBranch, elseBranch);
    }

    private NexaStatement parseForStatement() {
        consume(NexaTokenType.LPAREN, "For wajib memakai tanda (");

        NexaStatement initializer = null;
        if (!check(NexaTokenType.SEMICOLON)) {
            if (match(NexaTokenType.VAL)) {
                initializer = parseVariableDeclaration(true);
            } else if (match(NexaTokenType.VAR)) {
                initializer = parseVariableDeclaration(false);
            } else {
                initializer = new NexaExpressionStatement(parseExpression());
            }
        }

        consume(NexaTokenType.SEMICOLON, "For wajib memiliki pemisah ;");

        NexaExpression condition = null;
        if (!check(NexaTokenType.SEMICOLON)) {
            condition = parseExpression();
        }

        consume(NexaTokenType.SEMICOLON, "For wajib memiliki pemisah ;");

        NexaExpression update = null;
        if (!check(NexaTokenType.RPAREN)) {
            update = parseExpression();
        }

        consume(NexaTokenType.RPAREN, "For wajib memakai tanda )");
        NexaStatement body = parseBranchStatement();
        return new NexaForStatement(initializer, condition, update, body);
    }

    private NexaStatement parseSwitchStatement() {
        consume(NexaTokenType.LPAREN, "Switch wajib memakai tanda (");
        NexaExpression subject = parseExpression();
        consume(NexaTokenType.RPAREN, "Switch wajib memakai tanda )");
        consume(NexaTokenType.LBRACE, "Switch wajib memakai block");

        List<NexaSwitchCase> cases = new ArrayList<>();
        List<NexaStatement> defaultStatements = List.of();

        skipTerminators();
        while (!check(NexaTokenType.RBRACE) && !isAtEnd()) {
            if (match(NexaTokenType.CASE)) {
                NexaExpression matchExpression = parseExpression();
                consume(NexaTokenType.COLON, "Case wajib memakai :");
                cases.add(new NexaSwitchCase(matchExpression, parseSwitchClauseStatements()));
                continue;
            }

            if (match(NexaTokenType.DEFAULT)) {
                consume(NexaTokenType.COLON, "Default wajib memakai :");
                defaultStatements = parseSwitchClauseStatements();
                continue;
            }

            NexaToken token = peek();
            throw new NexaScriptException("Isi switch hanya boleh case atau default",
                    token.line(),
                    token.column());
        }

        consume(NexaTokenType.RBRACE, "Switch wajib ditutup dengan }");
        return new NexaSwitchStatement(subject, List.copyOf(cases), List.copyOf(defaultStatements));
    }

    private NexaStatement parseReturnStatement() {
        if (check(NexaTokenType.NEWLINE) || check(NexaTokenType.SEMICOLON) || check(NexaTokenType.RBRACE)
                || check(NexaTokenType.EOF)) {
            return new NexaReturnStatement(null);
        }
        return new NexaReturnStatement(parseExpression());
    }

    private NexaBlockStatement parseBlockStatement() {
        List<NexaStatement> statements = new ArrayList<>();
        skipTerminators();
        while (!check(NexaTokenType.RBRACE) && !isAtEnd()) {
            statements.add(parseStatement());
            skipTerminators();
        }
        consume(NexaTokenType.RBRACE, "Block wajib ditutup dengan }");
        return new NexaBlockStatement(List.copyOf(statements));
    }

    private List<NexaStatement> parseSwitchClauseStatements() {
        List<NexaStatement> statements = new ArrayList<>();
        skipTerminators();
        while (!check(NexaTokenType.CASE)
                && !check(NexaTokenType.DEFAULT)
                && !check(NexaTokenType.RBRACE)
                && !isAtEnd()) {
            statements.add(parseStatement());
            skipTerminators();
        }
        return List.copyOf(statements);
    }

    private NexaStatement parseBranchStatement() {
        if (match(NexaTokenType.LBRACE)) {
            return parseBlockStatement();
        }
        return parseStatement();
    }

    private NexaExpression parseExpression() {
        return parseAssignment();
    }

    private NexaExpression parseAssignment() {
        NexaExpression left = parseNullCoalesce();

        if (match(NexaTokenType.EQUAL, NexaTokenType.PLUS_EQUAL, NexaTokenType.MINUS_EQUAL,
                NexaTokenType.STAR_EQUAL, NexaTokenType.SLASH_EQUAL)) {
            NexaToken operator = previous();
            NexaExpression right = parseAssignment();
            return new NexaAssignmentExpression(left, operator.text(), right, operator.line(), operator.column());
        }

        return left;
    }

    private NexaExpression parseNullCoalesce() {
        NexaExpression expression = parseLogicalOr();
        while (match(NexaTokenType.QUESTION_QUESTION)) {
            NexaToken operator = previous();
            NexaExpression right = parseLogicalOr();
            expression = new NexaBinaryExpression(expression, operator.text(), right, operator.line(), operator.column());
        }
        return expression;
    }

    private NexaExpression parseLogicalOr() {
        NexaExpression expression = parseLogicalAnd();
        while (match(NexaTokenType.PIPE_PIPE)) {
            NexaToken operator = previous();
            NexaExpression right = parseLogicalAnd();
            expression = new NexaBinaryExpression(expression, operator.text(), right, operator.line(), operator.column());
        }
        return expression;
    }

    private NexaExpression parseLogicalAnd() {
        NexaExpression expression = parseEquality();
        while (match(NexaTokenType.AMPERSAND_AMPERSAND)) {
            NexaToken operator = previous();
            NexaExpression right = parseEquality();
            expression = new NexaBinaryExpression(expression, operator.text(), right, operator.line(), operator.column());
        }
        return expression;
    }

    private NexaExpression parseEquality() {
        NexaExpression expression = parseComparison();
        while (match(NexaTokenType.EQUAL_EQUAL, NexaTokenType.BANG_EQUAL)) {
            NexaToken operator = previous();
            NexaExpression right = parseComparison();
            expression = new NexaBinaryExpression(expression, operator.text(), right, operator.line(), operator.column());
        }
        return expression;
    }

    private NexaExpression parseComparison() {
        NexaExpression expression = parseTerm();
        while (match(NexaTokenType.GREATER, NexaTokenType.GREATER_EQUAL, NexaTokenType.LESS,
                NexaTokenType.LESS_EQUAL)) {
            NexaToken operator = previous();
            NexaExpression right = parseTerm();
            expression = new NexaBinaryExpression(expression, operator.text(), right, operator.line(), operator.column());
        }
        return expression;
    }

    private NexaExpression parseTerm() {
        NexaExpression expression = parseFactor();
        while (match(NexaTokenType.PLUS, NexaTokenType.MINUS)) {
            NexaToken operator = previous();
            NexaExpression right = parseFactor();
            expression = new NexaBinaryExpression(expression, operator.text(), right, operator.line(), operator.column());
        }
        return expression;
    }

    private NexaExpression parseFactor() {
        NexaExpression expression = parseUnary();
        while (match(NexaTokenType.STAR, NexaTokenType.SLASH, NexaTokenType.PERCENT)) {
            NexaToken operator = previous();
            NexaExpression right = parseUnary();
            expression = new NexaBinaryExpression(expression, operator.text(), right, operator.line(), operator.column());
        }
        return expression;
    }

    private NexaExpression parseUnary() {
        if (match(NexaTokenType.BANG, NexaTokenType.MINUS, NexaTokenType.PLUS)) {
            NexaToken operator = previous();
            return new NexaUnaryExpression(operator.text(), parseUnary(), operator.line(), operator.column());
        }
        return parsePostfix();
    }

    private NexaExpression parsePostfix() {
        NexaExpression expression = parsePrimary();
        while (true) {
            if (match(NexaTokenType.LPAREN)) {
                expression = finishCall(expression);
                continue;
            }
            if (match(NexaTokenType.DOT)) {
                NexaToken property = consume(NexaTokenType.IDENTIFIER, "Nama property wajib diisi");
                expression = new NexaPropertyAccessExpression(
                        expression,
                        property.text(),
                        false,
                        property.line(),
                        property.column());
                continue;
            }
            if (match(NexaTokenType.SAFE_DOT)) {
                NexaToken property = consume(NexaTokenType.IDENTIFIER, "Nama property wajib diisi");
                expression = new NexaPropertyAccessExpression(
                        expression,
                        property.text(),
                        true,
                        property.line(),
                        property.column());
                continue;
            }
            if (match(NexaTokenType.LBRACKET)) {
                NexaToken token = previous();
                NexaExpression index = parseExpression();
                consume(NexaTokenType.RBRACKET, "Index access wajib ditutup dengan ]");
                expression = new NexaIndexAccessExpression(expression, index, token.line(), token.column());
                continue;
            }
            break;
        }
        return expression;
    }

    private NexaExpression finishCall(NexaExpression callee) {
        NexaToken token = previous();
        List<NexaExpression> arguments = new ArrayList<>();
        if (!check(NexaTokenType.RPAREN)) {
            do {
                arguments.add(parseExpression());
            } while (match(NexaTokenType.COMMA));
        }
        consume(NexaTokenType.RPAREN, "Pemanggilan fungsi wajib ditutup dengan )");
        return new NexaCallExpression(callee, List.copyOf(arguments), token.line(), token.column());
    }

    private NexaExpression parsePrimary() {
        if (match(NexaTokenType.FUNCTION)) {
            return parseFunctionExpression();
        }
        if (match(NexaTokenType.TRUE)) {
            return new NexaLiteralExpression(Boolean.TRUE);
        }
        if (match(NexaTokenType.FALSE)) {
            return new NexaLiteralExpression(Boolean.FALSE);
        }
        if (match(NexaTokenType.NULL)) {
            return new NexaLiteralExpression(null);
        }
        if (match(NexaTokenType.NUMBER)) {
            String text = previous().text();
            return new NexaLiteralExpression(text.contains(".") ? Double.parseDouble(text) : Long.parseLong(text));
        }
        if (match(NexaTokenType.STRING)) {
            return new NexaLiteralExpression(previous().text());
        }
        if (match(NexaTokenType.TEMPLATE)) {
            NexaToken token = previous();
            return new NexaTemplateExpression(token.text(), token.line(), token.column());
        }
        if (match(NexaTokenType.IDENTIFIER)) {
            NexaToken token = previous();
            return new NexaIdentifierExpression(token.text(), token.line(), token.column());
        }
        if (match(NexaTokenType.LPAREN)) {
            NexaExpression expression = parseExpression();
            consume(NexaTokenType.RPAREN, "Expression wajib ditutup dengan )");
            return new NexaGroupingExpression(expression);
        }
        if (match(NexaTokenType.LBRACKET)) {
            return parseArrayExpression();
        }
        if (match(NexaTokenType.LBRACE)) {
            return parseObjectExpression();
        }

        NexaToken token = peek();
        throw new NexaScriptException("Expression tidak valid", token.line(), token.column());
    }

    private NexaFunctionExpression parseFunctionExpression() {
        NexaToken token = previous();
        List<String> parameters = parseFunctionParameters();
        NexaBlockStatement body = parseFunctionBody();
        return new NexaFunctionExpression(parameters, body, token.line(), token.column());
    }

    private List<String> parseFunctionParameters() {
        consume(NexaTokenType.LPAREN, "Function wajib memakai tanda (");
        List<String> parameters = new ArrayList<>();
        if (!check(NexaTokenType.RPAREN)) {
            do {
                NexaToken parameter = consume(NexaTokenType.IDENTIFIER, "Nama parameter wajib diisi");
                parameters.add(parameter.text());
            } while (match(NexaTokenType.COMMA));
        }
        consume(NexaTokenType.RPAREN, "Function wajib memakai tanda )");
        return List.copyOf(parameters);
    }

    private NexaBlockStatement parseFunctionBody() {
        if (match(NexaTokenType.ARROW)) {
            NexaExpression expression = parseExpression();
            return new NexaBlockStatement(List.of(new NexaReturnStatement(expression)));
        }

        consume(NexaTokenType.LBRACE, "Function wajib memakai body");
        return parseBlockStatement();
    }

    private NexaExpression parseArrayExpression() {
        List<NexaExpression> elements = new ArrayList<>();
        skipTerminators();
        if (!check(NexaTokenType.RBRACKET)) {
            do {
                skipTerminators();
                elements.add(parseExpression());
                skipTerminators();
            } while (match(NexaTokenType.COMMA));
        }
        consume(NexaTokenType.RBRACKET, "Array wajib ditutup dengan ]");
        return new NexaArrayExpression(List.copyOf(elements));
    }

    private NexaExpression parseObjectExpression() {
        List<NexaObjectField> fields = new ArrayList<>();
        skipTerminators();
        if (!check(NexaTokenType.RBRACE)) {
            do {
                skipTerminators();
                NexaToken keyToken;
                if (match(NexaTokenType.IDENTIFIER, NexaTokenType.STRING)) {
                    keyToken = previous();
                } else {
                    NexaToken token = peek();
                    throw new NexaScriptException("Key object wajib berupa identifier atau string",
                            token.line(),
                            token.column());
                }

                consume(NexaTokenType.COLON, "Object field wajib memakai :");
                fields.add(new NexaObjectField(keyToken.text(), parseExpression()));
                skipTerminators();
            } while (match(NexaTokenType.COMMA));
        }
        consume(NexaTokenType.RBRACE, "Object wajib ditutup dengan }");
        return new NexaObjectExpression(List.copyOf(fields));
    }

    private boolean match(NexaTokenType... types) {
        for (NexaTokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private NexaToken consume(NexaTokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        NexaToken token = peek();
        throw new NexaScriptException(message, token.line(), token.column());
    }

    private boolean check(NexaTokenType type) {
        if (isAtEnd()) {
            return type == NexaTokenType.EOF;
        }
        return peek().type() == type;
    }

    private boolean checkNext(NexaTokenType type) {
        if (current + 1 >= tokens.size()) {
            return false;
        }
        return tokens.get(current + 1).type() == type;
    }

    private NexaToken advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type() == NexaTokenType.EOF;
    }

    private NexaToken peek() {
        return tokens.get(current);
    }

    private NexaToken previous() {
        return tokens.get(current - 1);
    }

    private void skipTerminators() {
        while (match(NexaTokenType.NEWLINE, NexaTokenType.SEMICOLON)) {
        }
    }
}


