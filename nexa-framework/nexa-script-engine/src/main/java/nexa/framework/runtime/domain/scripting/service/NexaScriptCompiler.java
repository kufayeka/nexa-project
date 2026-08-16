package nexa.framework.runtime.domain.scripting.service;

import nexa.framework.runtime.domain.scripting.api.ScriptCompiler;
import nexa.framework.runtime.domain.scripting.api.CompiledScript;

import nexa.framework.runtime.domain.deployment.exception.ValidationException;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaCompiledScript;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaParser;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaProgram;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaScriptException;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaTokenizer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Nexa DSL compiler. Compilation is a control-plane operation; execution uses
 * the immutable CompiledScript returned by this compiler.
 */
public final class NexaScriptCompiler implements ScriptCompiler {

    private final ConcurrentMap<String, CompiledScript> workspaceCache = new ConcurrentHashMap<>();

    @Override
    public CompiledScript compile(String scriptSource, String sourceName) {
        return compileUncached(scriptSource, sourceName);
    }

    /** Compile once and retain the compiled program for the lifetime of a workspace deployment. */
    public CompiledScript compile(String scriptSource, String sourceName, String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return compile(scriptSource, sourceName);
        }
        String key = cacheKey(workspaceId, sourceName, scriptSource);
        return workspaceCache.computeIfAbsent(key, ignored -> compileUncached(scriptSource, sourceName));
    }

    private CompiledScript compileUncached(String scriptSource, String sourceName) {
        if (scriptSource == null || scriptSource.isBlank()) {
            throw new ValidationException("Nexa script must not be empty: " + sourceName);
        }
        try {
            NexaTokenizer tokenizer = new NexaTokenizer(scriptSource);
            NexaParser parser = new NexaParser(tokenizer.tokenize());
            NexaProgram program = parser.parseProgram();
            return new NexaCompiledScript(sourceName, scriptSource, program);
        } catch (NexaScriptException exception) {
            throw new ValidationException(formatDiagnostic("compile", sourceName, scriptSource, exception));
        }
    }

    /** Dry-run validation never populates the deployed workspace cache. */
    @Override
    public boolean validate(String scriptSource, Map<String, Object> errorContainer) {
        try {
            compileUncached(scriptSource, "validation_dry_run");
            return true;
        } catch (NexaScriptException exception) {
            putDiagnostic(errorContainer, exception.line(), exception.column(), exception.getMessage());
            return false;
        } catch (Exception exception) {
            putDiagnostic(errorContainer, 1, 1, exception.getMessage());
            return false;
        }
    }

    @Override
    public void clearWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return;
        }
        String prefix = workspaceId + "\u0000";
        workspaceCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public void dispose() {
        workspaceCache.clear();
    }

    public int cachedWorkspaceScriptCount() {
        return workspaceCache.size();
    }

    private static String cacheKey(String workspaceId, String sourceName, String source) {
        return workspaceId + "\u0000" + String.valueOf(sourceName) + "\u0000" + String.valueOf(source);
    }

    private static void putDiagnostic(Map<String, Object> errorContainer, int line, int column, String message) {
        if (errorContainer != null) {
            errorContainer.put("line", line);
            errorContainer.put("column", column);
            errorContainer.put("message", message);
        }
    }

    public static ValidationException runtimeError(String sourceName, String scriptSource, NexaScriptException exception) {
        return new ValidationException(formatDiagnostic("runtime", sourceName, scriptSource, exception));
    }

    private static String formatDiagnostic(
            String phase,
            String sourceName,
            String scriptSource,
            NexaScriptException exception) {
        String[] parts = parseSourceName(sourceName);
        String sourceLine = resolveSourceLine(scriptSource, exception.line());
        return "[nexa-script-error]"
                + " phase=" + phase
                + " workspace=" + parts[0]
                + " flow=" + parts[1]
                + " node=" + parts[2]
                + " line=" + exception.line()
                + " column=" + exception.column()
                + " sourceLine=" + sourceLine
                + " message=" + exception.getMessage();
    }

    private static String[] parseSourceName(String sourceName) {
        String[] parts = sourceName == null ? new String[0] : sourceName.split(":", 3);
        String workspace = parts.length > 0 ? parts[0] : "unknown";
        String flow = parts.length > 1 ? parts[1] : "unknown";
        String node = parts.length > 2 ? parts[2] : "unknown";
        return new String[] { workspace, flow, node };
    }

    private static String resolveSourceLine(String scriptSource, int lineNumber) {
        if (scriptSource == null || scriptSource.isBlank() || lineNumber < 1) {
            return "";
        }
        String[] lines = scriptSource.split("\\R", -1);
        if (lineNumber > lines.length) {
            return "";
        }
        return lines[lineNumber - 1].trim();
    }
}
