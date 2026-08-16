package nexa.framework.runtime.domain.scripting.api;

import java.util.Map;

public interface ScriptCompiler {

    CompiledScript compile(String scriptSource, String sourceName);

    /**
     * Workspace-aware compilation hook. Implementations may cache the compiled
     * program for the lifetime of a deployment. The default keeps backwards
     * compatibility for engines that do not need workspace-scoped caching.
     */
    default CompiledScript compile(String scriptSource, String sourceName, String workspaceId) {
        return compile(scriptSource, sourceName);
    }

    default boolean validate(String scriptSource, Map<String, Object> errorContainer) {
        try {
            compile(scriptSource, "validation_dry_run");
            return true;
        } catch (Exception e) {
            if (errorContainer != null) {
                errorContainer.put("message", e.getMessage());
            }
            return false;
        }
    }

    default void clearWorkspace(String workspaceId) {
    }

    default void dispose() {
    }
}
