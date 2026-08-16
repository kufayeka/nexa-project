package nexa.compiler.ir;

import java.util.*;

import nexa.compiler.lang.NexaFrontend;
import nexa.compiler.lang.NexaSemanticChecker;

/** Compiler phase boundary: source -> typed frontend -> verified Nexa IR. */
public final class NexaIrCompiler {
    public record Diagnostic(String message) {}

    public record Result(
            NexaIr.Program ir,
            List<Diagnostic> diagnostics) {
        public Result {
            diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        }

        public boolean success() {
            return ir != null && diagnostics.isEmpty();
        }
    }

    private final NexaFrontend frontend;
    private final NexaIrLowerer lowerer;
    private final NexaIrVerifier verifier;

    public NexaIrCompiler() {
        this(new NexaFrontend(), new NexaIrLowerer(), new NexaIrVerifier());
    }

    public NexaIrCompiler(
            NexaFrontend frontend,
            NexaIrLowerer lowerer,
            NexaIrVerifier verifier) {
        this.frontend = Objects.requireNonNull(frontend);
        this.lowerer = Objects.requireNonNull(lowerer);
        this.verifier = Objects.requireNonNull(verifier);
    }

    public Result compile(String source) {
        NexaFrontend.Result frontendResult = frontend.compile(source);
        if (!frontendResult.success()) {
            return new Result(null, frontendResult.diagnostics().stream()
                    .map(NexaIrCompiler::frontendDiagnostic)
                    .toList());
        }

        NexaIr.Program ir = lowerer.lower(frontendResult.ast());
        List<Diagnostic> diagnostics = verifier.verify(ir).stream()
                .map(d -> new Diagnostic(d.message()))
                .toList();

        return new Result(diagnostics.isEmpty() ? ir : null, diagnostics);
    }

    private static Diagnostic frontendDiagnostic(NexaSemanticChecker.Diagnostic diagnostic) {
        return new Diagnostic(diagnostic.message() + " @ " + diagnostic.span());
    }
}
