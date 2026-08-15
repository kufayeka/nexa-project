package nexa.framework.runtime.domain.scripting.internal.nexa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.scripting.model.DefaultScriptExecutionResult;
import nexa.framework.runtime.domain.scripting.api.ScriptExecutionControl;
import nexa.framework.runtime.domain.scripting.api.ScriptExecutionResult;
import nexa.framework.runtime.api.helpers.DeepCopyUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NexaRuntime {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Random RANDOM = new Random();

    private final Deque<Map<String, Binding>> scopes;
    private final ScriptExecutionControl control;

    public NexaRuntime(RuntimeMessage inputMessage, ScriptExecutionControl control) {
        this.scopes = new ArrayDeque<>();
        this.control = control;
        Map<String, Binding> globals = new LinkedHashMap<>();
        globals.put("msg", new Binding(false, normalizeMessage(inputMessage)));
        globals.put("Json", new Binding(false, Builtins.JSON));
        globals.put("Math", new Binding(false, Builtins.MATH));
        globals.put("DateTime", new Binding(false, Builtins.DATE_TIME));
        globals.put("Regex", new Binding(false, Builtins.REGEX));
        globals.put("send", new Binding(false, Builtins.send(control)));
        for (NexaRuntimeExtension extension : ServiceLoader.load(NexaRuntimeExtension.class)) {
            Map<String, Object> extensionGlobals = extension.globals();
            if (extensionGlobals == null || extensionGlobals.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Object> entry : extensionGlobals.entrySet()) {
                globals.put(entry.getKey(), new Binding(false, entry.getValue()));
            }
        }
        scopes.push(globals);
    }

    public ScriptExecutionResult execute(NexaProgram program) {
        EmissionCollector collector = new EmissionCollector();
        ScriptExecutionControl executionControl = new ScriptExecutionControl(collector);
        NexaRuntime runtime = new NexaRuntime(new RuntimeMessage(), executionControl);
        throw new UnsupportedOperationException();
    }

    public DefaultScriptExecutionResult evaluate(NexaProgram program) {
        EmissionCollector collector = new EmissionCollector();
        ScriptExecutionControl executionControl = new ScriptExecutionControl(collector);
        NexaRuntime runtime = new NexaRuntime(new RuntimeMessage(), executionControl);
        throw new UnsupportedOperationException();
    }

    public void executeStatements(List<NexaStatement> statements) {
        for (NexaStatement statement : statements) {
            executeStatement(statement);
        }
    }

    private void executeStatement(NexaStatement statement) {
        if (statement instanceof NexaBlockStatement blockStatement) {
            pushScope();
            try {
                executeStatements(blockStatement.statements());
            } finally {
                popScope();
            }
            return;
        }

        if (statement instanceof NexaVariableDeclaration variableDeclaration) {
            Object value = variableDeclaration.initializer() == null
                    ? null
                    : evaluateExpression(variableDeclaration.initializer());
            define(variableDeclaration.name(), variableDeclaration.readOnly(), value, variableDeclaration.line(),
                    variableDeclaration.column());
            return;
        }

        if (statement instanceof NexaFunctionDeclaration functionDeclaration) {
            define(functionDeclaration.name(), true, null, functionDeclaration.line(), functionDeclaration.column());
            Object function = createUserFunction(
                    functionDeclaration.name(),
                    functionDeclaration.parameters(),
                    functionDeclaration.body(),
                    functionDeclaration.line(),
                    functionDeclaration.column());
            writeInternal(functionDeclaration.name(), function, functionDeclaration.line(),
                    functionDeclaration.column());
            return;
        }

        if (statement instanceof NexaExpressionStatement expressionStatement) {
            evaluateExpression(expressionStatement.expression());
            return;
        }

        if (statement instanceof NexaIfStatement ifStatement) {
            if (isTruthy(evaluateExpression(ifStatement.condition()))) {
                executeStatement(ifStatement.thenBranch());
            } else if (ifStatement.elseBranch() != null) {
                executeStatement(ifStatement.elseBranch());
            }
            return;
        }

        if (statement instanceof NexaSwitchStatement switchStatement) {
            executeSwitchStatement(switchStatement);
            return;
        }

        if (statement instanceof NexaForStatement forStatement) {
            pushScope();
            try {
                if (forStatement.initializer() != null) {
                    executeStatement(forStatement.initializer());
                }
                while (forStatement.condition() == null || isTruthy(evaluateExpression(forStatement.condition()))) {
                    executeStatement(forStatement.body());
                    if (forStatement.update() != null) {
                        evaluateExpression(forStatement.update());
                    }
                }
            } finally {
                popScope();
            }
            return;
        }

        if (statement instanceof NexaReturnStatement returnStatement) {
            Object value = returnStatement.expression() == null ? null
                    : evaluateExpression(returnStatement.expression());
            throw new ReturnSignal(value);
        }
    }

    public Object evaluateExpression(NexaExpression expression) {
        if (expression instanceof NexaLiteralExpression literalExpression) {
            return DeepCopyUtil.deepCopyValue(literalExpression.value());
        }
        if (expression instanceof NexaTemplateExpression templateExpression) {
            return renderTemplate(templateExpression);
        }
        if (expression instanceof NexaIdentifierExpression identifierExpression) {
            return read(identifierExpression.name(), identifierExpression.line(), identifierExpression.column());
        }
        if (expression instanceof NexaGroupingExpression groupingExpression) {
            return evaluateExpression(groupingExpression.expression());
        }
        if (expression instanceof NexaUnaryExpression unaryExpression) {
            return evaluateUnary(unaryExpression);
        }
        if (expression instanceof NexaBinaryExpression binaryExpression) {
            return evaluateBinary(binaryExpression);
        }
        if (expression instanceof NexaAssignmentExpression assignmentExpression) {
            return assign(assignmentExpression);
        }
        if (expression instanceof NexaCallExpression callExpression) {
            return call(callExpression);
        }
        if (expression instanceof NexaFunctionExpression functionExpression) {
            return createUserFunction(
                    null,
                    functionExpression.parameters(),
                    functionExpression.body(),
                    functionExpression.line(),
                    functionExpression.column());
        }
        if (expression instanceof NexaPropertyAccessExpression propertyAccessExpression) {
            return readProperty(propertyAccessExpression);
        }
        if (expression instanceof NexaIndexAccessExpression indexAccessExpression) {
            return readIndex(indexAccessExpression);
        }
        if (expression instanceof NexaArrayExpression arrayExpression) {
            List<Object> values = new ArrayList<>();
            for (NexaExpression element : arrayExpression.elements()) {
                values.add(evaluateExpression(element));
            }
            return values;
        }
        if (expression instanceof NexaObjectExpression objectExpression) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (NexaObjectField field : objectExpression.fields()) {
                values.put(field.key(), evaluateExpression(field.value()));
            }
            return values;
        }

        throw new IllegalStateException("Unknown expression " + expression.getClass().getSimpleName());
    }

    private void executeSwitchStatement(NexaSwitchStatement statement) {
        Object subject = evaluateExpression(statement.subject());
        for (NexaSwitchCase switchCase : statement.cases()) {
            Object candidate = evaluateExpression(switchCase.matchExpression());
            if (isSwitchMatch(subject, candidate, 1, 1)) {
                executeStatements(switchCase.statements());
                return;
            }
        }

        if (!statement.defaultStatements().isEmpty()) {
            executeStatements(statement.defaultStatements());
        }
    }

    private Object evaluateUnary(NexaUnaryExpression expression) {
        Object value = evaluateExpression(expression.operand());
        return switch (expression.operator()) {
            case "!" -> !isTruthy(value);
            case "-" -> -toNumber(value, expression.line(), expression.column());
            case "+" -> toNumber(value, expression.line(), expression.column());
            default -> throw error("Operator unary tidak didukung: " + expression.operator(),
                    expression.line(),
                    expression.column());
        };
    }

    private Object evaluateBinary(NexaBinaryExpression expression) {
        Object left = evaluateExpression(expression.left());

        if ("??".equals(expression.operator())) {
            return left != null ? left : evaluateExpression(expression.right());
        }

        if ("||".equals(expression.operator())) {
            return isTruthy(left) ? left : evaluateExpression(expression.right());
        }

        if ("&&".equals(expression.operator())) {
            return !isTruthy(left) ? left : evaluateExpression(expression.right());
        }

        Object right = evaluateExpression(expression.right());

        return switch (expression.operator()) {
            case "+" -> add(left, right, expression.line(), expression.column());
            case "-" -> toNumber(left, expression.line(), expression.column())
                    - toNumber(right, expression.line(), expression.column());
            case "*" -> toNumber(left, expression.line(), expression.column())
                    * toNumber(right, expression.line(), expression.column());
            case "/" -> toNumber(left, expression.line(), expression.column())
                    / toNumber(right, expression.line(), expression.column());
            case "%" -> toNumber(left, expression.line(), expression.column())
                    % toNumber(right, expression.line(), expression.column());
            case ">" -> compare(left, right, expression.line(), expression.column()) > 0;
            case ">=" -> compare(left, right, expression.line(), expression.column()) >= 0;
            case "<" -> compare(left, right, expression.line(), expression.column()) < 0;
            case "<=" -> compare(left, right, expression.line(), expression.column()) <= 0;
            case "==" -> Objects.equals(left, right);
            case "!=" -> !Objects.equals(left, right);
            default -> throw error("Operator tidak didukung: " + expression.operator(),
                    expression.line(),
                    expression.column());
        };
    }

    private Object assign(NexaAssignmentExpression expression) {
        Object value = evaluateExpression(expression.value());
        Object assignedValue = value;

        if (!"=".equals(expression.operator())) {
            Object current = evaluateExpression(expression.target());
            assignedValue = switch (expression.operator()) {
                case "+=" -> add(current, value, expression.line(), expression.column());
                case "-=" -> toNumber(current, expression.line(), expression.column())
                        - toNumber(value, expression.line(), expression.column());
                case "*=" -> toNumber(current, expression.line(), expression.column())
                        * toNumber(value, expression.line(), expression.column());
                case "/=" -> toNumber(current, expression.line(), expression.column())
                        / toNumber(value, expression.line(), expression.column());
                default -> throw error("Assignment operator tidak didukung: " + expression.operator(),
                        expression.line(),
                        expression.column());
            };
        }

        if (expression.target() instanceof NexaIdentifierExpression identifierExpression) {
            write(identifierExpression.name(), assignedValue, identifierExpression.line(),
                    identifierExpression.column());
            return assignedValue;
        }

        if (expression.target() instanceof NexaPropertyAccessExpression propertyAccessExpression) {
            if (propertyAccessExpression.safe()) {
                throw error("Assignment tidak boleh memakai optional chaining",
                        propertyAccessExpression.line(),
                        propertyAccessExpression.column());
            }
            Object target = evaluateExpression(propertyAccessExpression.target());
            writePropertyValue(target,
                    propertyAccessExpression.property(),
                    assignedValue,
                    propertyAccessExpression.line(),
                    propertyAccessExpression.column());
            return assignedValue;
        }

        if (expression.target() instanceof NexaIndexAccessExpression indexAccessExpression) {
            Object target = evaluateExpression(indexAccessExpression.target());
            Object index = evaluateExpression(indexAccessExpression.index());
            writeIndexValue(target, index, assignedValue, indexAccessExpression.line(), indexAccessExpression.column());
            return assignedValue;
        }

        throw error("Target assignment tidak valid", expression.line(), expression.column());
    }

    private Object call(NexaCallExpression expression) {
        Object callee = evaluateExpression(expression.callee());
        List<Object> arguments = new ArrayList<>(expression.arguments().size());
        for (NexaExpression argument : expression.arguments()) {
            arguments.add(evaluateExpression(argument));
        }
        return invokeCallable(callee, arguments, expression.line(), expression.column());
    }

    private Object readProperty(NexaPropertyAccessExpression expression) {
        Object target = evaluateExpression(expression.target());
        if (target == null) {
            if (expression.safe()) {
                return null;
            }
            throw error("Tidak bisa membaca property dari null", expression.line(), expression.column());
        }

        Object member = resolveMember(target, expression.property(), expression.line(), expression.column());
        if (member == MissingValue.INSTANCE) {
            return null;
        }
        return member;
    }

    private Object readIndex(NexaIndexAccessExpression expression) {
        Object target = evaluateExpression(expression.target());
        Object index = evaluateExpression(expression.index());
        if (target == null) {
            throw error("Tidak bisa membaca index dari null", expression.line(), expression.column());
        }
        if (target instanceof List<?> list) {
            int resolvedIndex = toIndex(index, expression.line(), expression.column());
            if (resolvedIndex < 0 || resolvedIndex >= list.size()) {
                return null;
            }
            return list.get(resolvedIndex);
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(String.valueOf(index));
        }
        if (target instanceof String value) {
            int resolvedIndex = toIndex(index, expression.line(), expression.column());
            if (resolvedIndex < 0 || resolvedIndex >= value.length()) {
                return null;
            }
            return String.valueOf(value.charAt(resolvedIndex));
        }
        throw error("Index access hanya mendukung array, object, atau string",
                expression.line(),
                expression.column());
    }

    private Object resolveMember(Object target, String property, int line, int column) {
        if (target instanceof Map<?, ?> map) {
            if (map.containsKey(property)) {
                return map.get(property);
            }
            return memberFunction(target, property, line, column);
        }
        if (target instanceof List<?> list) {
            if ("length".equals(property)) {
                return (long) list.size();
            }
            return memberFunction(target, property, line, column);
        }
        if (target instanceof String text) {
            if ("length".equals(property)) {
                return (long) text.length();
            }
            return memberFunction(target, property, line, column);
        }
        if (target instanceof DateValue) {
            return memberFunction(target, property, line, column);
        }
        if (target instanceof DateTimeValue) {
            return memberFunction(target, property, line, column);
        }
        if (target instanceof Number || target instanceof Boolean) {
            return memberFunction(target, property, line, column);
        }
        if (target instanceof NexaHostObject hostObject) {
            return hostObject.member(property, line, column);
        }
        return memberFunction(target, property, line, column);
    }

    private Object memberFunction(Object target, String property, int line, int column) {
        return switch (property) {
            case "toString", "toBool", "toNumber", "toDate", "toDateTime",
                    "trim", "replace", "replaceAll", "split", "startsWith", "endsWith", "includes",
                    "substring", "slice", "toUpperCase", "toLowerCase", "join",
                    "push", "pop", "shift", "unshift", "indexOf", "splice", "toISOString", "match",
                    "map", "filter", "reduce", "forEach", "find", "some", "every" ->
                new BoundMethod(target, property);
            default -> MissingValue.INSTANCE;
        };
    }

    private String renderTemplate(NexaTemplateExpression expression) {
        StringBuilder builder = new StringBuilder();
        String template = expression.template();
        int cursor = 0;
        while (cursor < template.length()) {
            int start = template.indexOf("${", cursor);
            if (start < 0) {
                builder.append(template.substring(cursor));
                break;
            }
            builder.append(template, cursor, start);
            int end = findTemplateExpressionEnd(template, start + 2, expression.line(), expression.column());
            String nestedExpression = template.substring(start + 2, end);
            NexaExpression parsed = new NexaParser(new NexaTokenizer(nestedExpression).tokenize())
                    .parseExpressionOnly();
            Object value = evaluateExpression(parsed);
            builder.append(stringifyValue(value));
            cursor = end + 1;
        }
        return builder.toString();
    }

    private NexaUserFunction createUserFunction(
            String name,
            List<String> parameters,
            NexaBlockStatement body,
            int line,
            int column) {
        return new NexaUserFunction(name, List.copyOf(parameters), body, captureScopeChain(), line, column);
    }

    private int findTemplateExpressionEnd(String template, int start, int line, int column) {
        int depth = 1;
        for (int index = start; index < template.length(); index++) {
            char current = template.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        throw error("Template expression tidak ditutup", line, column);
    }

    private void define(String name, boolean readOnly, Object value, int line, int column) {
        Map<String, Binding> scope = scopes.peek();
        if (scope.containsKey(name)) {
            throw error("Variabel sudah ada: " + name, line, column);
        }
        scope.put(name, new Binding(readOnly, value));
    }

    private List<Map<String, Binding>> captureScopeChain() {
        return List.copyOf(new ArrayList<>(scopes));
    }

    private Object read(String name, int line, int column) {
        for (Map<String, Binding> scope : scopes) {
            Binding binding = scope.get(name);
            if (binding != null) {
                return binding.value;
            }
        }
        throw error("Variabel tidak ditemukan: " + name, line, column);
    }

    private void write(String name, Object value, int line, int column) {
        for (Map<String, Binding> scope : scopes) {
            Binding binding = scope.get(name);
            if (binding != null) {
                if (binding.readOnly) {
                    throw error("Val tidak boleh diubah: " + name, line, column);
                }
                binding.value = value;
                return;
            }
        }
        throw error("Variabel tidak ditemukan: " + name, line, column);
    }

    private void writeInternal(String name, Object value, int line, int column) {
        for (Map<String, Binding> scope : scopes) {
            Binding binding = scope.get(name);
            if (binding != null) {
                binding.value = value;
                return;
            }
        }
        throw error("Variabel tidak ditemukan: " + name, line, column);
    }

    @SuppressWarnings("unchecked")
    private void writePropertyValue(Object target, String property, Object value, int line, int column) {
        if (target instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).put(property, value);
            return;
        }
        throw error("Property assignment hanya mendukung object", line, column);
    }

    @SuppressWarnings("unchecked")
    private void writeIndexValue(Object target, Object index, Object value, int line, int column) {
        if (target instanceof List<?> list) {
            int resolvedIndex = toIndex(index, line, column);
            if (resolvedIndex < 0 || resolvedIndex >= list.size()) {
                throw error("Index di luar batas", line, column);
            }
            ((List<Object>) list).set(resolvedIndex, value);
            return;
        }
        if (target instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).put(String.valueOf(index), value);
            return;
        }
        throw error("Index assignment hanya mendukung array atau object", line, column);
    }

    private void pushScope() {
        scopes.push(new LinkedHashMap<>());
    }

    private void popScope() {
        scopes.pop();
    }

    private double toNumber(Object value, int line, int column) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                throw error("String tidak bisa dikonversi ke number: " + text, line, column);
            }
        }
        if (value instanceof Boolean bool) {
            return bool ? 1D : 0D;
        }
        throw error("Value bukan number: " + value, line, column);
    }

    private int compare(Object left, Object right, int line, int column) {
        if (left instanceof Number || right instanceof Number) {
            return Double.compare(toNumber(left, line, column), toNumber(right, line, column));
        }
        if (left instanceof String leftText && right instanceof String rightText) {
            return leftText.compareTo(rightText);
        }
        if (left instanceof DateValue leftDate && right instanceof DateValue rightDate) {
            return leftDate.date.compareTo(rightDate.date);
        }
        if (left instanceof DateTimeValue leftDateTime && right instanceof DateTimeValue rightDateTime) {
            return leftDateTime.instant.compareTo(rightDateTime.instant);
        }
        throw error("Perbandingan tidak didukung", line, column);
    }

    private Object add(Object left, Object right, int line, int column) {
        if (left instanceof String || right instanceof String) {
            return stringifyValue(left) + stringifyValue(right);
        }
        return toNumber(left, line, column) + toNumber(right, line, column);
    }

    private int toIndex(Object value, int line, int column) {
        int resolved = (int) Math.floor(toNumber(value, line, column));
        return resolved;
    }

    private int normalizeSliceIndex(int index, int size) {
        int normalized = index < 0 ? size + index : index;
        if (normalized < 0) {
            return 0;
        }
        return Math.min(normalized, size);
    }

    private boolean isSwitchMatch(Object subject, Object candidate, int line, int column) {
        if (subject instanceof Number || candidate instanceof Number) {
            return Double.compare(toNumber(subject, line, column), toNumber(candidate, line, column)) == 0;
        }
        return Objects.equals(subject, candidate);
    }

    private boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0D;
        }
        if (value instanceof String text) {
            return !text.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private Map<String, Object> normalizeMessage(RuntimeMessage message) {
        return DeepCopyUtil.deepCopyMap(message.values());
    }

    private NexaScriptException error(String message, int line, int column) {
        return new NexaScriptException(message, line, column);
    }

    private Object invokeCallable(Object callee, List<Object> arguments, int line, int column) {
        if (callee instanceof NexaCallable callable) {
            return callable.call(this, arguments, line, column);
        }
        if (callee instanceof NexaUserFunction function) {
            return invokeUserFunction(function, arguments, line, column);
        }
        throw error("Value bukan function", line, column);
    }

    private Object invokeUserFunction(NexaUserFunction function, List<Object> arguments, int line, int column) {
        List<Map<String, Binding>> previousScopes = new ArrayList<>(scopes);
        Map<String, Binding> localScope = new LinkedHashMap<>();
        for (int index = 0; index < function.parameters().size(); index++) {
            Object value = index < arguments.size() ? arguments.get(index) : null;
            localScope.put(function.parameters().get(index), new Binding(false, value));
        }

        scopes.clear();
        scopes.addFirst(localScope);
        for (Map<String, Binding> capturedScope : function.capturedScopes()) {
            scopes.addLast(capturedScope);
        }

        try {
            executeStatement(function.body());
            return null;
        } catch (ReturnSignal signal) {
            return signal.value();
        } finally {
            scopes.clear();
            scopes.addAll(previousScopes);
        }
    }

    public DefaultScriptExecutionResult result(EmissionCollector collector) {
        return DefaultScriptExecutionResult.of(collector.emittedByPort);
    }

    private static final class Binding {
        private final boolean readOnly;
        private Object value;

        private Binding(boolean readOnly, Object value) {
            this.readOnly = readOnly;
            this.value = value;
        }
    }

    private record NexaUserFunction(
            String name,
            List<String> parameters,
            NexaBlockStatement body,
            List<Map<String, Binding>> capturedScopes,
            int line,
            int column) {

        private String displayName() {
            return name == null ? "<lambda>" : name;
        }
    }

    public interface NexaCallable {
        Object call(NexaRuntime runtime, List<Object> arguments, int line, int column);
    }

    private record BoundMethod(Object target, String method) implements NexaCallable {
        @Override
        @SuppressWarnings("unchecked")
        public Object call(NexaRuntime runtime, List<Object> arguments, int line, int column) {
            return switch (method) {
                case "toString" -> runtime.stringifyValue(target);
                case "toBool" -> runtime.isTruthy(target);
                case "toNumber" -> runtime.toNumber(target, line, column);
                case "toDate" -> runtime.toDate(target, line, column);
                case "toDateTime" -> runtime.toDateTime(target, line, column);
                case "trim" -> runtime.requireString(target, line, column).trim();
                case "replace" -> runtime.requireString(target, line, column)
                        .replace(runtime.requireStringArg(arguments, 0, line, column),
                                runtime.requireStringArg(arguments, 1, line, column));
                case "replaceAll" -> runtime.requireString(target, line, column)
                        .replaceAll(runtime.requireStringArg(arguments, 0, line, column),
                                runtime.requireStringArg(arguments, 1, line, column));
                case "split" -> List.of(runtime.requireString(target, line, column)
                        .split(Pattern.quote(runtime.requireStringArg(arguments, 0, line, column)), -1));
                case "startsWith" -> runtime.requireString(target, line, column)
                        .startsWith(runtime.requireStringArg(arguments, 0, line, column));
                case "endsWith" -> runtime.requireString(target, line, column)
                        .endsWith(runtime.requireStringArg(arguments, 0, line, column));
                case "includes" -> {
                    if (target instanceof String text) {
                        yield text.contains(runtime.requireStringArg(arguments, 0, line, column));
                    }
                    if (target instanceof List<?> list) {
                        yield list.contains(arguments.getFirst());
                    }
                    throw runtime.error("includes hanya mendukung string atau array", line, column);
                }
                case "substring" -> {
                    String text = runtime.requireString(target, line, column);
                    int start = runtime.requireIntArg(arguments, 0, line, column);
                    int end = arguments.size() > 1 ? runtime.requireIntArg(arguments, 1, line, column) : text.length();
                    yield text.substring(Math.max(0, start), Math.min(text.length(), end));
                }
                case "slice" -> {
                    if (target instanceof String text) {
                        int start = runtime.normalizeSliceIndex(
                                runtime.requireIntArg(arguments, 0, line, column),
                                text.length());
                        int end = arguments.size() > 1
                                ? runtime.normalizeSliceIndex(runtime.requireIntArg(arguments, 1, line, column),
                                        text.length())
                                : text.length();
                        yield text.substring(Math.min(start, end), Math.max(start, end));
                    }
                    List<Object> list = runtime.requireList(target, line, column);
                    int start = runtime.normalizeSliceIndex(runtime.requireIntArg(arguments, 0, line, column),
                            list.size());
                    int end = arguments.size() > 1
                            ? runtime.normalizeSliceIndex(runtime.requireIntArg(arguments, 1, line, column),
                                    list.size())
                            : list.size();
                    yield new ArrayList<>(list.subList(Math.min(start, end), Math.max(start, end)));
                }
                case "toUpperCase" -> runtime.requireString(target, line, column).toUpperCase();
                case "toLowerCase" -> runtime.requireString(target, line, column).toLowerCase();
                case "join" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    String separator = arguments.isEmpty() ? "," : runtime.requireStringArg(arguments, 0, line, column);
                    List<String> values = new ArrayList<>(list.size());
                    for (Object value : list) {
                        values.add(runtime.stringifyValue(value));
                    }
                    yield String.join(separator, values);
                }
                case "push" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    list.addAll(arguments);
                    yield (long) list.size();
                }
                case "pop" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    yield list.isEmpty() ? null : list.removeLast();
                }
                case "shift" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    yield list.isEmpty() ? null : list.removeFirst();
                }
                case "unshift" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    list.addAll(0, arguments);
                    yield (long) list.size();
                }
                case "indexOf" -> {
                    if (target instanceof String text) {
                        yield (long) text.indexOf(runtime.requireStringArg(arguments, 0, line, column));
                    }
                    yield (long) runtime.requireList(target, line, column).indexOf(arguments.getFirst());
                }
                case "splice" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    int start = runtime.normalizeSliceIndex(runtime.requireIntArg(arguments, 0, line, column),
                            list.size());
                    int deleteCount = arguments.size() > 1
                            ? Math.max(0, runtime.requireIntArg(arguments, 1, line, column))
                            : list.size() - start;
                    int end = Math.min(list.size(), start + deleteCount);
                    List<Object> removed = new ArrayList<>(list.subList(start, end));
                    list.subList(start, end).clear();
                    if (arguments.size() > 2) {
                        list.addAll(start, new ArrayList<>(arguments.subList(2, arguments.size())));
                    }
                    yield removed;
                }
                case "map" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    Object callback = runtime.requireArg(arguments, 0, line, column);
                    List<Object> result = new ArrayList<>(list.size());
                    for (int index = 0; index < list.size(); index++) {
                        result.add(runtime.invokeCallable(callback,
                                List.of(list.get(index), (long) index, list),
                                line,
                                column));
                    }
                    yield result;
                }
                case "filter" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    Object callback = runtime.requireArg(arguments, 0, line, column);
                    List<Object> result = new ArrayList<>();
                    for (int index = 0; index < list.size(); index++) {
                        Object item = list.get(index);
                        Object decision = runtime.invokeCallable(callback,
                                List.of(item, (long) index, list),
                                line,
                                column);
                        if (runtime.isTruthy(decision)) {
                            result.add(item);
                        }
                    }
                    yield result;
                }
                case "reduce" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    Object callback = runtime.requireArg(arguments, 0, line, column);
                    if (list.isEmpty() && arguments.size() < 2) {
                        throw runtime.error("reduce pada array kosong butuh initial value", line, column);
                    }

                    int startIndex = 0;
                    Object accumulator;
                    if (arguments.size() > 1) {
                        accumulator = arguments.get(1);
                    } else {
                        accumulator = list.getFirst();
                        startIndex = 1;
                    }

                    for (int index = startIndex; index < list.size(); index++) {
                        accumulator = runtime.invokeCallable(callback,
                                List.of(accumulator, list.get(index), (long) index, list),
                                line,
                                column);
                    }
                    yield accumulator;
                }
                case "forEach" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    Object callback = runtime.requireArg(arguments, 0, line, column);
                    for (int index = 0; index < list.size(); index++) {
                        runtime.invokeCallable(callback,
                                List.of(list.get(index), (long) index, list),
                                line,
                                column);
                    }
                    yield null;
                }
                case "find" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    Object callback = runtime.requireArg(arguments, 0, line, column);
                    for (int index = 0; index < list.size(); index++) {
                        Object item = list.get(index);
                        Object decision = runtime.invokeCallable(callback,
                                List.of(item, (long) index, list),
                                line,
                                column);
                        if (runtime.isTruthy(decision)) {
                            yield item;
                        }
                    }
                    yield null;
                }
                case "some" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    Object callback = runtime.requireArg(arguments, 0, line, column);
                    for (int index = 0; index < list.size(); index++) {
                        Object decision = runtime.invokeCallable(callback,
                                List.of(list.get(index), (long) index, list),
                                line,
                                column);
                        if (runtime.isTruthy(decision)) {
                            yield true;
                        }
                    }
                    yield false;
                }
                case "every" -> {
                    List<Object> list = runtime.requireList(target, line, column);
                    Object callback = runtime.requireArg(arguments, 0, line, column);
                    for (int index = 0; index < list.size(); index++) {
                        Object decision = runtime.invokeCallable(callback,
                                List.of(list.get(index), (long) index, list),
                                line,
                                column);
                        if (!runtime.isTruthy(decision)) {
                            yield false;
                        }
                    }
                    yield true;
                }
                case "toISOString" -> {
                    if (target instanceof DateValue dateValue) {
                        yield dateValue.date.toString();
                    }
                    yield runtime.toDateTime(target, line, column).instant.toString();
                }
                case "match" -> {
                    String text = runtime.requireString(target, line, column);
                    String pattern = runtime.requireStringArg(arguments, 0, line, column);
                    Matcher matcher = Pattern.compile(pattern).matcher(text);
                    List<String> matches = new ArrayList<>();
                    while (matcher.find()) {
                        matches.add(matcher.group());
                    }
                    yield matches;
                }
                default -> throw runtime.error("Method tidak didukung: " + method, line, column);
            };
        }
    }

    private enum Builtins implements NexaHostObject {
        JSON {
            @Override
            public Object member(String name, int line, int column) {
                return switch (name) {
                    case "parse" -> (NexaCallable) (runtime, arguments, callLine, callColumn) -> {
                        String json = runtime.requireStringArg(arguments, 0, callLine, callColumn);
                        try {
                            return OBJECT_MAPPER.readValue(json, Object.class);
                        } catch (JsonProcessingException exception) {
                            throw runtime.error("Json.parse gagal: " + exception.getOriginalMessage(),
                                    callLine,
                                    callColumn);
                        }
                    };
                    case "stringify" -> (NexaCallable) (runtime, arguments, callLine, callColumn) -> {
                        try {
                            return OBJECT_MAPPER.writeValueAsString(arguments.getFirst());
                        } catch (JsonProcessingException exception) {
                            throw runtime.error("Json.stringify gagal: " + exception.getOriginalMessage(),
                                    callLine,
                                    callColumn);
                        }
                    };
                    default -> throw new NexaScriptException("Member Json tidak dikenal: " + name, line, column);
                };
            }
        },
        MATH {
            @Override
            public Object member(String name, int line, int column) {
                return (NexaCallable) (runtime, arguments, callLine, callColumn) -> switch (name) {
                    case "abs" -> Math.abs(runtime.requireNumberArg(arguments, 0, callLine, callColumn));
                    case "round" -> (long) Math.round(runtime.requireNumberArg(arguments, 0, callLine, callColumn));
                    case "floor" -> Math.floor(runtime.requireNumberArg(arguments, 0, callLine, callColumn));
                    case "ceil" -> Math.ceil(runtime.requireNumberArg(arguments, 0, callLine, callColumn));
                    case "max" -> arguments.stream()
                            .mapToDouble(argument -> runtime.toNumber(argument, callLine, callColumn))
                            .max()
                            .orElse(0D);
                    case "min" -> arguments.stream()
                            .mapToDouble(argument -> runtime.toNumber(argument, callLine, callColumn))
                            .min()
                            .orElse(0D);
                    case "random" -> RANDOM.nextDouble();
                    case "sin" -> Math.sin(runtime.requireNumberArg(arguments, 0, callLine, callColumn));
                    case "cos" -> Math.cos(runtime.requireNumberArg(arguments, 0, callLine, callColumn));
                    case "sqrt" -> Math.sqrt(runtime.requireNumberArg(arguments, 0, callLine, callColumn));
                    case "pow" -> Math.pow(runtime.requireNumberArg(arguments, 0, callLine, callColumn),
                            runtime.requireNumberArg(arguments, 1, callLine, callColumn));
                    case "log" -> Math.log(runtime.requireNumberArg(arguments, 0, callLine, callColumn));
                    default -> throw runtime.error("Member Math tidak dikenal: " + name, callLine, callColumn);
                };
            }
        },
        DATE_TIME {
            @Override
            public Object member(String name, int line, int column) {
                return switch (name) {
                    case "now" ->
                        (NexaCallable) (runtime, arguments, callLine, callColumn) -> new DateTimeValue(Instant.now());
                    default -> throw new NexaScriptException("Member DateTime tidak dikenal: " + name, line, column);
                };
            }
        },
        REGEX {
            @Override
            public Object member(String name, int line, int column) {
                return (NexaCallable) (runtime, arguments, callLine, callColumn) -> switch (name) {
                    case "match" -> {
                        String text = runtime.requireStringArg(arguments, 0, callLine, callColumn);
                        String pattern = runtime.requireStringArg(arguments, 1, callLine, callColumn);
                        Matcher matcher = Pattern.compile(pattern).matcher(text);
                        List<String> matches = new ArrayList<>();
                        while (matcher.find()) {
                            matches.add(matcher.group());
                        }
                        yield matches;
                    }
                    case "replace" -> runtime.requireStringArg(arguments, 0, callLine, callColumn)
                            .replaceAll(runtime.requireStringArg(arguments, 1, callLine, callColumn),
                                    runtime.requireStringArg(arguments, 2, callLine, callColumn));
                    default -> throw runtime.error("Member Regex tidak dikenal: " + name, callLine, callColumn);
                };
            }
        };

        static NexaCallable send(ScriptExecutionControl control) {
            return (runtime, arguments, line, column) -> {
                if (arguments.size() == 1) {
                    control.send(runtime.toMessagePayload(arguments.getFirst(), line, column));
                    return null;
                }
                if (arguments.size() == 2) {
                    Object target = arguments.getFirst();
                    Object message = runtime.toMessagePayload(arguments.get(1), line, column);
                    if (target instanceof String port) {
                        control.send(port, message);
                        return null;
                    }
                    if (target instanceof List<?> ports) {
                        List<String> resolved = new ArrayList<>(ports.size());
                        for (Object port : ports) {
                            resolved.add(String.valueOf(port));
                        }
                        control.send(resolved, message);
                        return null;
                    }
                }
                throw runtime.error("send hanya mendukung send(msg), send(port, msg), send(ports, msg)",
                        line,
                        column);
            };
        }
    }

    private record DateTimeValue(Instant instant) {
        @Override
        public String toString() {
            return instant.toString();
        }
    }

    private record DateValue(LocalDate date) {
        @Override
        public String toString() {
            return date.toString();
        }
    }

    private record EmissionCollector(Map<String, List<RuntimeMessage>> emittedByPort)
            implements ScriptExecutionControl.ScriptEmissionCollector {

        private EmissionCollector() {
            this(new LinkedHashMap<>());
        }

        @Override
        public void emit(String port, RuntimeMessage message) {
            emittedByPort.computeIfAbsent(port, ignored -> new ArrayList<>()).add(message);
        }
    }

    public static final class ReturnSignal extends RuntimeException {
        private final Object value;

        private ReturnSignal(Object value) {
            this.value = value;
        }

        public Object value() {
            return value;
        }
    }

    private enum MissingValue {
        INSTANCE
    }

    private String requireString(Object value, int line, int column) {
        if (value instanceof String text) {
            return text;
        }
        throw error("Value bukan string", line, column);
    }

    private String stringifyValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Double number && number % 1D == 0D) {
            return String.valueOf(number.longValue());
        }
        if (value instanceof Float number && number % 1F == 0F) {
            return String.valueOf(number.longValue());
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<Object> requireList(Object value, int line, int column) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw error("Value bukan array", line, column);
    }

    private Object toMessagePayload(Object value, int line, int column) {
        if (value instanceof Map<?, ?>) {
            return value;
        }
        throw error("Message harus berupa object", line, column);
    }

    private DateTimeValue toDateTime(Object value, int line, int column) {
        if (value instanceof DateTimeValue dateTimeValue) {
            return dateTimeValue;
        }
        if (value instanceof DateValue dateValue) {
            return new DateTimeValue(dateValue.date.atStartOfDay().toInstant(ZoneOffset.UTC));
        }
        if (value instanceof String text) {
            try {
                return new DateTimeValue(Instant.parse(text));
            } catch (DateTimeParseException exception) {
                throw error("String bukan ISO datetime valid: " + text, line, column);
            }
        }
        throw error("Value tidak bisa dikonversi ke DateTime", line, column);
    }

    private DateValue toDate(Object value, int line, int column) {
        if (value instanceof DateValue dateValue) {
            return dateValue;
        }
        if (value instanceof DateTimeValue dateTimeValue) {
            return new DateValue(dateTimeValue.instant.atZone(ZoneOffset.UTC).toLocalDate());
        }
        if (value instanceof String text) {
            try {
                return new DateValue(LocalDate.parse(text));
            } catch (DateTimeParseException ignored) {
                try {
                    Instant instant = Instant.parse(text);
                    return new DateValue(instant.atZone(ZoneOffset.UTC).toLocalDate());
                } catch (DateTimeParseException exception) {
                    throw error("String bukan ISO date/datetime valid: " + text, line, column);
                }
            }
        }
        throw error("Value tidak bisa dikonversi ke Date", line, column);
    }

    private String requireStringArg(List<Object> arguments, int index, int line, int column) {
        return requireString(requireArg(arguments, index, line, column), line, column);
    }

    private int requireIntArg(List<Object> arguments, int index, int line, int column) {
        return (int) Math.floor(toNumber(requireArg(arguments, index, line, column), line, column));
    }

    private double requireNumberArg(List<Object> arguments, int index, int line, int column) {
        return toNumber(requireArg(arguments, index, line, column), line, column);
    }

    private Object requireArg(List<Object> arguments, int index, int line, int column) {
        if (index >= arguments.size()) {
            throw error("Argumen kurang", line, column);
        }
        return arguments.get(index);
    }
}
