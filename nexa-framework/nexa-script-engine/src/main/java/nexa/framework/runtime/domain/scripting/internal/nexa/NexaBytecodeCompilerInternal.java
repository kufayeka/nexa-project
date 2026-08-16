package nexa.framework.runtime.domain.scripting.internal.nexa;

import nexa.framework.runtime.domain.scripting.bytecode.NexaBytecodeInstruction;
import nexa.framework.runtime.domain.scripting.bytecode.NexaBytecodeOpcode;
import nexa.framework.runtime.domain.scripting.bytecode.NexaBytecodeProgram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Low-level AST -> bytecode lowering. Kept beside the AST because the AST is intentionally package-private. */
public final class NexaBytecodeCompilerInternal {
    private final List<NexaBytecodeInstruction> code = new ArrayList<>();
    private final List<Object> constants = new ArrayList<>();
    private final Map<String, Integer> locals = new HashMap<>();

    public NexaBytecodeProgram compile(String sourceName, String source) {
        NexaProgram program = new NexaParser(new NexaTokenizer(source).tokenize()).parseProgram();
        for (NexaStatement statement : program.statements()) compileStatement(statement);
        if (code.isEmpty() || code.get(code.size() - 1).opcode() != NexaBytecodeOpcode.RETURN) {
            emit(NexaBytecodeOpcode.CONST, constant(null));
            emit(NexaBytecodeOpcode.RETURN);
        }
        return new NexaBytecodeProgram(sourceName, code, constants, locals.size());
    }

    private void compileStatement(NexaStatement statement) {
        if (statement instanceof NexaVariableDeclaration s) {
            int slot = locals.computeIfAbsent(s.name(), ignored -> locals.size());
            if (s.initializer() == null) emit(NexaBytecodeOpcode.CONST, constant(null));
            else compileExpression(s.initializer());
            emit(NexaBytecodeOpcode.STORE_LOCAL, slot);
            return;
        }
        if (statement instanceof NexaExpressionStatement s) {
            compileExpression(s.expression());
            emit(NexaBytecodeOpcode.POP);
            return;
        }
        if (statement instanceof NexaReturnStatement s) {
            if (s.expression() == null) emit(NexaBytecodeOpcode.CONST, constant(null));
            else compileExpression(s.expression());
            emit(NexaBytecodeOpcode.RETURN);
            return;
        }
        if (statement instanceof NexaBlockStatement s) {
            for (NexaStatement child : s.statements()) compileStatement(child);
            return;
        }
        if (statement instanceof NexaIfStatement s) {
            compileExpression(s.condition());
            int jumpFalse = emit(NexaBytecodeOpcode.JUMP_IF_FALSE, -1);
            compileStatement(s.thenBranch());
            if (s.elseBranch() != null) {
                int jumpEnd = emit(NexaBytecodeOpcode.JUMP, -1);
                patch(jumpFalse, code.size());
                compileStatement(s.elseBranch());
                patch(jumpEnd, code.size());
            } else {
                patch(jumpFalse, code.size());
            }
            return;
        }
        if (statement instanceof NexaForStatement s) {
            if (s.initializer() != null) compileStatement(s.initializer());
            int loopStart = code.size();
            if (s.condition() != null) {
                compileExpression(s.condition());
                int exit = emit(NexaBytecodeOpcode.JUMP_IF_FALSE, -1);
                compileStatement(s.body());
                if (s.update() != null) { compileExpression(s.update()); emit(NexaBytecodeOpcode.POP); }
                emit(NexaBytecodeOpcode.JUMP, loopStart);
                patch(exit, code.size());
            } else {
                compileStatement(s.body());
                if (s.update() != null) { compileExpression(s.update()); emit(NexaBytecodeOpcode.POP); }
                emit(NexaBytecodeOpcode.JUMP, loopStart);
            }
            return;
        }
        throw new IllegalArgumentException("Unsupported statement for bytecode: " + statement.getClass().getSimpleName());
    }

    private void compileExpression(NexaExpression expression) {
        if (expression instanceof NexaLiteralExpression e) { emit(NexaBytecodeOpcode.CONST, constant(e.value())); return; }
        if (expression instanceof NexaIdentifierExpression e) {
            Integer slot = locals.get(e.name());
            if (slot != null) emit(NexaBytecodeOpcode.LOAD_LOCAL, slot);
            else emit(NexaBytecodeOpcode.LOAD_GLOBAL, e.name());
            return;
        }
        if (expression instanceof NexaGroupingExpression e) { compileExpression(e.expression()); return; }
        if (expression instanceof NexaUnaryExpression e) {
            compileExpression(e.operand());
            switch (e.operator()) {
                case "-" -> emit(NexaBytecodeOpcode.NEGATE);
                case "!" -> emit(NexaBytecodeOpcode.NOT);
                default -> throw unsupported(e.operator());
            }
            return;
        }
        if (expression instanceof NexaBinaryExpression e) {
            compileExpression(e.left());
            compileExpression(e.right());
            switch (e.operator()) {
                case "+" -> emit(NexaBytecodeOpcode.ADD);
                case "-" -> emit(NexaBytecodeOpcode.SUB);
                case "*" -> emit(NexaBytecodeOpcode.MUL);
                case "/" -> emit(NexaBytecodeOpcode.DIV);
                case "%" -> emit(NexaBytecodeOpcode.MOD);
                case "==", "===" -> emit(NexaBytecodeOpcode.EQUAL);
                case "!=", "!==" -> emit(NexaBytecodeOpcode.NOT_EQUAL);
                case "<" -> emit(NexaBytecodeOpcode.LESS);
                case "<=" -> emit(NexaBytecodeOpcode.LESS_EQUAL);
                case ">" -> emit(NexaBytecodeOpcode.GREATER);
                case ">=" -> emit(NexaBytecodeOpcode.GREATER_EQUAL);
                case "&&" -> emit(NexaBytecodeOpcode.AND);
                case "||" -> emit(NexaBytecodeOpcode.OR);
                default -> throw unsupported(e.operator());
            }
            return;
        }
        if (expression instanceof NexaPropertyAccessExpression e) {
            compileExpression(e.target());
            emit(NexaBytecodeOpcode.LOAD_PROPERTY, e.property());
            return;
        }
        if (expression instanceof NexaIndexAccessExpression e) {
            compileExpression(e.target());
            compileExpression(e.index());
            emit(NexaBytecodeOpcode.LOAD_INDEX);
            return;
        }
        if (expression instanceof NexaArrayExpression e) {
            for (NexaExpression element : e.elements()) compileExpression(element);
            emit(NexaBytecodeOpcode.MAKE_ARRAY, e.elements().size());
            return;
        }
        if (expression instanceof NexaObjectExpression e) {
            for (NexaObjectField field : e.fields()) {
                emit(NexaBytecodeOpcode.CONST, constant(field.key()));
                compileExpression(field.value());
            }
            emit(NexaBytecodeOpcode.MAKE_OBJECT, e.fields().size());
            return;
        }
        if (expression instanceof NexaAssignmentExpression e) {
            compileAssignment(e);
            return;
        }
        if (expression instanceof NexaCallExpression e) {
            if (!(e.callee() instanceof NexaIdentifierExpression callee)) {
                throw new IllegalArgumentException("Only named host calls can be lowered to bytecode yet");
            }
            for (NexaExpression argument : e.arguments()) compileExpression(argument);
            emit(NexaBytecodeOpcode.CALL_HOST, callee.name(), e.arguments().size());
            return;
        }
        throw new IllegalArgumentException("Unsupported expression for bytecode: " + expression.getClass().getSimpleName());
    }

    private void compileAssignment(NexaAssignmentExpression e) {
        if (!(e.target() instanceof NexaIdentifierExpression target)) {
            throw new IllegalArgumentException("Bytecode assignment currently supports local variables only");
        }
        Integer slot = locals.get(target.name());
        if (slot == null) throw new IllegalArgumentException("Unknown local: " + target.name());
        compileExpression(e.value());
        if (!"=".equals(e.operator())) throw new IllegalArgumentException("Compound assignment is not lowered yet: " + e.operator());
        emit(NexaBytecodeOpcode.STORE_LOCAL, slot);
        emit(NexaBytecodeOpcode.LOAD_LOCAL, slot);
    }

    private int constant(Object value) { constants.add(value); return constants.size() - 1; }

    private int emit(NexaBytecodeOpcode opcode, Object... operands) {
        code.add(NexaBytecodeInstruction.of(opcode, operands));
        return code.size() - 1;
    }

    private void patch(int instruction, int target) {
        NexaBytecodeInstruction old = code.get(instruction);
        code.set(instruction, NexaBytecodeInstruction.of(old.opcode(), target));
    }

    private IllegalArgumentException unsupported(String operator) {
        return new IllegalArgumentException("Unsupported Nexa operator in bytecode compiler: " + operator);
    }
}
