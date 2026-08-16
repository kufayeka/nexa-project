package nexa.compiler.api;

import java.util.Objects;

/** Structured compiler diagnostic; compilation must fail before deployment on errors. */
public record CompilationDiagnostic(
        Severity severity,
        String code,
        String message,
        int line,
        int column) {

    public CompilationDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
