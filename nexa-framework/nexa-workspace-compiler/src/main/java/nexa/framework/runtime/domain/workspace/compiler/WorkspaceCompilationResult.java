package nexa.framework.runtime.domain.workspace.compiler;

import java.util.List;

public record WorkspaceCompilationResult<T>(T artifact, List<WorkspaceCompilationDiagnostic> diagnostics) {
    public WorkspaceCompilationResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean success() {
        return diagnostics.stream().noneMatch(d -> d.severity() == WorkspaceCompilationDiagnostic.Severity.ERROR);
    }
}
