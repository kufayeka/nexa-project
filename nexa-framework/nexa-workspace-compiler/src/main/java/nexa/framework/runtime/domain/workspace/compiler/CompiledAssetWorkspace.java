package nexa.framework.runtime.domain.workspace.compiler;

import java.util.Map;

public record CompiledAssetWorkspace(
        String workspaceId,
        Map<String, String> assetTypes,
        Map<String, nexa.framework.runtime.domain.scripting.api.CompiledScript> scripts,
        NexaBytecodeProgram bytecode) {
    public CompiledAssetWorkspace {
        assetTypes = assetTypes == null ? Map.of() : Map.copyOf(assetTypes);
        scripts = scripts == null ? Map.of() : Map.copyOf(scripts);
        bytecode = bytecode == null ? NexaBytecodeProgram.empty() : bytecode;
    }

    public CompiledWorkspaceArtifact artifact() {
        return new CompiledWorkspaceArtifact(workspaceId, "phase1", scripts, assetTypes, bytecode);
    }
}
