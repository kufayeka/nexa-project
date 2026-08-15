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

public final class NexaScriptCompiler implements ScriptCompiler {

    @Override
    public CompiledScript compile(String scriptSource, String sourceName) {
        try {
            NexaTokenizer tokenizer = new NexaTokenizer(scriptSource);
            NexaParser parser = new NexaParser(tokenizer.tokenize());
            NexaProgram program = parser.parseProgram();
            return new NexaCompiledScript(sourceName, scriptSource, program);
        } catch (NexaScriptException exception) {
            throw new ValidationException(formatDiagnostic("compile", sourceName, scriptSource, exception));
        }
    }

    /**
     * Melakukan validasi sintaks skrip Nexa secara kering (dry-run).
     * Menyimpan rincian error ke map jika parsing gagal.
     */
    @Override
    public boolean validate(String scriptSource, Map<String, Object> errorContainer) {
        try {
            NexaTokenizer tokenizer = new NexaTokenizer(scriptSource);
            NexaParser parser = new NexaParser(tokenizer.tokenize());
            parser.parseProgram();
            return true;
        } catch (NexaScriptException exception) {
            if (errorContainer != null) {
                errorContainer.put("line", exception.line());
                errorContainer.put("column", exception.column());
                errorContainer.put("message", exception.getMessage());
            }
            return false;
        } catch (Exception e) {
            if (errorContainer != null) {
                errorContainer.put("line", 1);
                errorContainer.put("column", 1);
                errorContainer.put("message", e.getMessage());
            }
            return false;
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


