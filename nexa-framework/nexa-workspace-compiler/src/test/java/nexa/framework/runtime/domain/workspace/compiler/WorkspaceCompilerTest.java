package nexa.framework.runtime.domain.workspace.compiler;

import nexa.framework.runtime.domain.scripting.service.NexaScriptCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceCompilerTest {
    @Test
    void bytecodeContractIsImmutable() {
        NexaBytecodeProgram program = new NexaBytecodeProgram(
                List.of(BytecodeInstruction.of(BytecodeOpcode.LOAD_CONSTANT, 1), BytecodeInstruction.of(BytecodeOpcode.RETURN)),
                List.of(1));
        assertEquals(BytecodeOpcode.LOAD_CONSTANT, program.instructions().getFirst().opcode());
        assertThrows(UnsupportedOperationException.class, () -> program.instructions().clear());
    }

    @Test
    void assetWorkspaceCompilesAllScriptsBeforeRuntime() {
        AssetWorkspaceCompiler compiler = new AssetWorkspaceCompiler(new NexaScriptCompiler());
        CompiledAssetWorkspace workspace = compiler.compile(
                "asset-test",
                Map.of("/motor/speed", "INT32", "/motor/running", "BOOLEAN"),
                List.of(
                        new AssetWorkspaceCompiler.AssetScriptSource("/motor/speed", "return 100;"),
                        new AssetWorkspaceCompiler.AssetScriptSource("/motor/running", "return true;")
                ),
                new WorkspaceCompilationContext() {}
        );

        assertEquals("INT32", workspace.assetTypes().get("/motor/speed"));
        assertEquals(2, workspace.scripts().size());
        assertTrue(workspace.scripts().containsKey("/motor/speed"));
    }

    @Test
    void invalidAssetScriptFailsCompilation() {
        AssetWorkspaceCompiler compiler = new AssetWorkspaceCompiler(new NexaScriptCompiler());
        assertThrows(RuntimeException.class, () -> compiler.compile(
                "asset-test",
                Map.of("/motor/speed", "INT32"),
                List.of(new AssetWorkspaceCompiler.AssetScriptSource("/motor/speed", "return ; this is invalid")),
                null
        ));
    }
}
