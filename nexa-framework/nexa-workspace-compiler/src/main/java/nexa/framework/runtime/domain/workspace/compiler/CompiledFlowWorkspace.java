package nexa.framework.runtime.domain.workspace.compiler;

import java.util.Map;

public record CompiledFlowWorkspace(
        String workspaceId,
        nexa.framework.runtime.domain.deployment.model.CompiledWorkspace flowWorkspace,
        Map<String, String> symbols,
        NexaBytecodeProgram bytecode) {
    public CompiledFlowWorkspace {
        symbols = symbols == null ? Map.of() : Map.copyOf(symbols);
        bytecode = bytecode == null ? NexaBytecodeProgram.empty() : bytecode;
    }

    public CompiledWorkspaceArtifact artifact() {
        return new CompiledWorkspaceArtifact(workspaceId, "phase1", Map.of(), symbols, bytecode);
    }
}
