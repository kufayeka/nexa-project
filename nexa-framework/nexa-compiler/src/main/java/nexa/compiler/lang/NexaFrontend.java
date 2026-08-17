package nexa.compiler.lang;

import java.util.*;
import static nexa.compiler.lang.NexaAst.*;

public final class NexaFrontend {
    public record Result(Program ast, List<NexaSemanticChecker.Diagnostic> diagnostics) {
        public boolean success(){ return diagnostics.isEmpty(); }
    }

    public Result compile(String source){
        try {
            Program ast = new NexaParser(new NexaLexer(source).lex()).parse();
            List<NexaSemanticChecker.Diagnostic> diagnostics = new ArrayList<>(new NexaSemanticChecker().check(ast));
            checkConstAssignments(ast, diagnostics);
            return new Result(ast, List.copyOf(diagnostics));
        } catch(RuntimeException e) {
            return new Result(null, List.of(new NexaSemanticChecker.Diagnostic(e.getMessage(), new SourceSpan(0,0))));
        }
    }

    private static void checkConstAssignments(Program program, List<NexaSemanticChecker.Diagnostic> diagnostics) {
        Deque<Set<String>> scopes = new ArrayDeque<>();
        scopes.push(new HashSet<>());
        for (Stmt statement : program.statements()) checkConstStatement(statement, scopes, diagnostics);
    }

    private static void checkConstStatement(Stmt statement, Deque<Set<String>> scopes,
                                            List<NexaSemanticChecker.Diagnostic> diagnostics) {
        if (statement instanceof Let let) {
            if (let.constant()) scopes.peek().add(let.name());
            return;
        }
        if (statement instanceof Assign assign) {
            if (assign.target() instanceof Var variable && isConstant(variable.name(), scopes)) {
                diagnostics.add(new NexaSemanticChecker.Diagnostic(
                        "Cannot assign to constant '" + variable.name() + "'", assign.span()));
            }
            return;
        }
        if (statement instanceof For loop) {
            scopes.push(new HashSet<>());
            for (Stmt body : loop.body()) checkConstStatement(body, scopes, diagnostics);
            scopes.pop();
        }
    }

    private static boolean isConstant(String name, Deque<Set<String>> scopes) {
        for (Set<String> scope : scopes) if (scope.contains(name)) return true;
        return false;
    }
}