package nexa.compiler.ir;

import java.util.*;

import nexa.compiler.lang.NexaFrontend;
import nexa.compiler.lang.NexaSemanticChecker;

/** Compiler phase boundary: source -> typed frontend -> lowered/optimized/verified Nexa IR. */
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
    private final NexaIrOptimizer optimizer;
    private final NexaIrVerifier verifier;

    public NexaIrCompiler() {
        this(new NexaFrontend(), new NexaIrLowerer(), new NexaIrOptimizer(), new NexaIrVerifier());
    }

    public NexaIrCompiler(
            NexaFrontend frontend,
            NexaIrLowerer lowerer,
            NexaIrOptimizer optimizer,
            NexaIrVerifier verifier) {
        this.frontend = Objects.requireNonNull(frontend);
        this.lowerer = Objects.requireNonNull(lowerer);
        this.optimizer = Objects.requireNonNull(optimizer);
        this.verifier = Objects.requireNonNull(verifier);
    }

    /** Backwards-compatible constructor for callers that only customize the lowerer/verifier. */
    public NexaIrCompiler(
            NexaFrontend frontend,
            NexaIrLowerer lowerer,
            NexaIrVerifier verifier) {
        this(frontend, lowerer, new NexaIrOptimizer(), verifier);
    }

    public Result compile(String source) {
        NexaFrontend.Result frontendResult = frontend.compile(source);
        if (!frontendResult.success()) {
            return new Result(null, frontendResult.diagnostics().stream()
                    .map(NexaIrCompiler::frontendDiagnostic)
                    .toList());
        }

        NexaIr.Program lowered = lowerer.lower(frontendResult.ast());
        NexaIr.Program ir = optimizer.optimize(lowered);

        List<Diagnostic> diagnostics = verifier.verify(ir).stream()
                .map(d -> new Diagnostic(d.message()))
                .toList();

        return new Result(diagnostics.isEmpty() ? ir : null, diagnostics);
    }

    private static Diagnostic frontendDiagnostic(NexaSemanticChecker.Diagnostic diagnostic) {
        return new Diagnostic(diagnostic.message() + " @ " + diagnostic.span());
    }
}
