package nexa.framework.runtime.domain.workspace.compiler;

import java.util.Map;

/** Host boundary exposed to the compiler. Plugins implement resolution; the compiler stays plugin-agnostic. */
public interface WorkspaceCompilationContext {
    default String resolveAssetType(String normalizedPath) { return null; }
    default String resolveNodeType(String nodeId) { return null; }
    default String resolveResourceType(String resourceId) { return null; }
    default Map<String, String> assetSymbols() { return Map.of(); }
    default Map<String, String> nodeSymbols() { return Map.of(); }
}
