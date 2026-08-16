package nexa.compiler.lang;

import java.util.*;
import static nexa.compiler.lang.NexaAst.Program;

public final class NexaFrontend {
    public record Result(Program ast, List<NexaSemanticChecker.Diagnostic> diagnostics) { public boolean success(){return diagnostics.isEmpty();} }
    public Result compile(String source){
        try { Program ast=new NexaParser(new NexaLexer(source).lex()).parse(); return new Result(ast,new NexaSemanticChecker().check(ast)); }
        catch(RuntimeException e){ return new Result(null,List.of(new NexaSemanticChecker.Diagnostic(e.getMessage(),new SourceSpan(0,0)))); }
    }
}
