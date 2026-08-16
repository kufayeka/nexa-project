package nexa.compiler.api;

import java.util.Map;
import java.util.Objects;

/** Immutable input to a workspace compilation. */
public record CompilationRequest(
        String workspaceId,
        String source,
        CompilationTarget target,
        Map<String, String> symbols) {

    public CompilationRequest {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        symbols = symbols == null ? Map.of() : Map.copyOf(symbols);
    }
}
