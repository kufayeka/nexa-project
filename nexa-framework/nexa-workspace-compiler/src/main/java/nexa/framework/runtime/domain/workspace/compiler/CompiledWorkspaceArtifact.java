package nexa.framework.runtime.domain.workspace.compiler;

import nexa.framework.runtime.domain.scripting.api.CompiledScript;

import java.util.Map;
import java.util.Objects;

/** Common immutable artifact produced after workspace compilation. */
public record CompiledWorkspaceArtifact(
        String workspaceId,
        String compilerVersion,
        Map<String, CompiledScript> scripts,
        Map<String, String> symbols,
        NexaBytecodeProgram bytecode) {
    public CompiledWorkspaceArtifact {
        Objects.requireNonNull(workspaceId, "workspaceId");
        compilerVersion = compilerVersion == null ? "unknown" : compilerVersion;
        scripts = scripts == null ? Map.of() : Map.copyOf(scripts);
        symbols = symbols == null ? Map.of() : Map.copyOf(symbols);
        bytecode = bytecode == null ? NexaBytecodeProgram.empty() : bytecode;
    }
}
