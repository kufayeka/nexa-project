package nexa.framework.runtime.domain.workspace.compiler;

public record WorkspaceCompilationDiagnostic(
        Severity severity,
        String code,
        String workspaceId,
        String source,
        int line,
        int column,
        String message) {
    public enum Severity { ERROR, WARNING }
}
