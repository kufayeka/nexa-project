package nexa.compiler.api;

/** Compiler boundary used by flow and asset workspaces. */
public interface NexaCompiler {

    CompilationResult compile(CompilationRequest request);
}
