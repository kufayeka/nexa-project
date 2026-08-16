package nexa.framework.runtime.domain.workspace.compiler;

import nexa.framework.runtime.domain.scripting.api.CompiledScript;
import nexa.framework.runtime.domain.scripting.api.ScriptCompiler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Phase-1 compiler for Asset Workspaces. It resolves the asset symbol table once and compiles every script eagerly. */
public final class AssetWorkspaceCompiler {
    private final ScriptCompiler scriptCompiler;

    public AssetWorkspaceCompiler(ScriptCompiler scriptCompiler) {
        this.scriptCompiler = scriptCompiler;
    }

    public CompiledAssetWorkspace compile(
            String workspaceId,
            Map<String, String> assetTypes,
            List<AssetScriptSource> scripts,
            WorkspaceCompilationContext context) {
        if (workspaceId == null || workspaceId.isBlank()) throw new IllegalArgumentException("workspaceId must not be blank");
        Map<String, String> resolvedTypes = new LinkedHashMap<>();
        if (assetTypes != null) resolvedTypes.putAll(assetTypes);
        if (context != null) resolvedTypes.putAll(context.assetSymbols());

        Map<String, CompiledScript> compiled = new LinkedHashMap<>();
        if (scripts != null) {
            for (AssetScriptSource source : scripts) {
                if (source == null) continue;
                if (source.path() == null || source.path().isBlank()) throw new IllegalArgumentException("Asset script path must not be blank");
                if (source.source() == null || source.source().isBlank()) throw new IllegalArgumentException("Asset script source must not be blank: " + source.path());
                if (compiled.putIfAbsent(source.path(), scriptCompiler.compile(source.source(), workspaceId + ":asset:" + source.path(), workspaceId)) != null) {
                    throw new IllegalArgumentException("Duplicate asset script path: " + source.path());
                }
            }
        }
        return new CompiledAssetWorkspace(workspaceId, resolvedTypes, compiled, NexaBytecodeProgram.empty());
    }

    public record AssetScriptSource(String path, String source) {}
}
