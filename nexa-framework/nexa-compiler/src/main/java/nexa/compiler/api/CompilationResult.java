package nexa.compiler.api;

import java.util.List;
import java.util.Objects;

/** Result of compilation. The runtime consumes an immutable compiled artifact. */
public record CompilationResult(
        boolean successful,
        List<CompilationDiagnostic> diagnostics,
        CompiledArtifact artifact) {

    public CompilationResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        if (successful && artifact == null) {
            throw new IllegalArgumentException("Successful compilation requires an artifact");
        }
    }
}
