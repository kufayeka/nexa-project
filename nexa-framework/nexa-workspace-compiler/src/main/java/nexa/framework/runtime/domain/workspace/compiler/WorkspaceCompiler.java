package nexa.framework.runtime.domain.workspace.compiler;

/** Common compile contract for Flow and Asset Workspaces. */
public interface WorkspaceCompiler<S, T> {
    WorkspaceCompilationResult<T> compile(S source, WorkspaceCompilationContext context);
}
