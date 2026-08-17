package nexa.compiler.codegen;

import nexa.framework.runtime.api.NexaCompilerService;
import nexa.compiler.ir.NexaIrCompiler;
import nexa.compiler.ir.NexaIr;
import java.util.Map;

public final class NexaCompilerServiceImpl implements NexaCompilerService {
    @Override
    public byte[] compile(String programName, String source, Map<String, Integer> tagSlots) {
        NexaIrCompiler irCompiler = new NexaIrCompiler();
        NexaIrCompiler.Result result = irCompiler.compile(source);
        if (!result.success()) {
            throw new RuntimeException("Compilation failed: " + result.diagnostics());
        }
        NexaBytecodeCompiler bytecodeCompiler = new NexaBytecodeCompiler(tagSlots);
        return bytecodeCompiler.compile(result.ir(), programName);
    }
}
