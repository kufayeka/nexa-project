package nexa.framework.runtime.domain.scripting.internal.nexa;

import java.util.List;

sealed interface NexaStatement permits NexaBlockStatement, NexaExpressionStatement, NexaForStatement,
        NexaFunctionDeclaration, NexaIfStatement, NexaReturnStatement, NexaSwitchStatement, NexaVariableDeclaration {
}

record NexaBlockStatement(List<NexaStatement> statements) implements NexaStatement {
}

record NexaVariableDeclaration(boolean readOnly, String name, NexaExpression initializer, int line, int column)
        implements NexaStatement {
}

record NexaFunctionDeclaration(
        String name,
        List<String> parameters,
        NexaBlockStatement body,
        int line,
        int column) implements NexaStatement {
}

record NexaExpressionStatement(NexaExpression expression) implements NexaStatement {
}

record NexaIfStatement(
        NexaExpression condition,
        NexaStatement thenBranch,
        NexaStatement elseBranch) implements NexaStatement {
}

record NexaForStatement(
        NexaStatement initializer,
        NexaExpression condition,
        NexaExpression update,
        NexaStatement body) implements NexaStatement {
}

record NexaReturnStatement(NexaExpression expression) implements NexaStatement {
}

record NexaSwitchCase(NexaExpression matchExpression, List<NexaStatement> statements) {
}

record NexaSwitchStatement(
        NexaExpression subject,
        List<NexaSwitchCase> cases,
        List<NexaStatement> defaultStatements) implements NexaStatement {
}

sealed interface NexaExpression permits NexaArrayExpression, NexaAssignmentExpression, NexaBinaryExpression,
        NexaCallExpression, NexaFunctionExpression, NexaGroupingExpression, NexaIdentifierExpression, NexaIndexAccessExpression,
        NexaLiteralExpression, NexaObjectExpression, NexaPropertyAccessExpression, NexaTemplateExpression,
        NexaUnaryExpression {
}

record NexaLiteralExpression(Object value) implements NexaExpression {
}

record NexaTemplateExpression(String template, int line, int column) implements NexaExpression {
}

record NexaIdentifierExpression(String name, int line, int column) implements NexaExpression {
}

record NexaGroupingExpression(NexaExpression expression) implements NexaExpression {
}

record NexaUnaryExpression(String operator, NexaExpression operand, int line, int column) implements NexaExpression {
}

record NexaBinaryExpression(
        NexaExpression left,
        String operator,
        NexaExpression right,
        int line,
        int column) implements NexaExpression {
}

record NexaAssignmentExpression(
        NexaExpression target,
        String operator,
        NexaExpression value,
        int line,
        int column) implements NexaExpression {
}

record NexaCallExpression(
        NexaExpression callee,
        List<NexaExpression> arguments,
        int line,
        int column) implements NexaExpression {
}

record NexaFunctionExpression(
        List<String> parameters,
        NexaBlockStatement body,
        int line,
        int column) implements NexaExpression {
}

record NexaPropertyAccessExpression(
        NexaExpression target,
        String property,
        boolean safe,
        int line,
        int column) implements NexaExpression {
}

record NexaIndexAccessExpression(
        NexaExpression target,
        NexaExpression index,
        int line,
        int column) implements NexaExpression {
}

record NexaArrayExpression(List<NexaExpression> elements) implements NexaExpression {
}

record NexaObjectField(String key, NexaExpression value) {
}

record NexaObjectExpression(List<NexaObjectField> fields) implements NexaExpression {
}


