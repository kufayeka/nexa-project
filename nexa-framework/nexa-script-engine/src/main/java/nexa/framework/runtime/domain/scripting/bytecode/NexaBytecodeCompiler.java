package nexa.framework.runtime.domain.scripting.bytecode;

import nexa.framework.runtime.domain.scripting.internal.nexa.NexaBytecodeCompilerInternal;

/** Public compiler entry point. Parsing is performed once; the returned artifact is executable bytecode. */
public final class NexaBytecodeCompiler {
    public NexaBytecodeProgram compile(String sourceName, String source) {
        return new NexaBytecodeCompilerInternal().compile(sourceName, source);
    }
}
