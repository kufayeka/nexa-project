package nexa.framework.runtime.domain.scripting.service;

import nexa.framework.runtime.domain.scripting.api.CompiledScript;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

final class NexaScriptCompilerCacheTest {

    @Test
    void workspaceCompilationReusesImmutableCompiledProgram() {
        NexaScriptCompiler compiler = new NexaScriptCompiler();
        String source = "return 10 + 20;";

        CompiledScript first = compiler.compile(source, "ws:flow:node", "ws");
        CompiledScript second = compiler.compile(source, "ws:flow:node", "ws");

        assertSame(first, second);
        assertEquals(1, compiler.cachedWorkspaceScriptCount());

        CompiledScript differentNode = compiler.compile(source, "ws:flow:other", "ws");
        assertNotSame(first, differentNode);
        assertEquals(2, compiler.cachedWorkspaceScriptCount());
    }

    @Test
    void clearingWorkspaceDropsOnlyThatWorkspaceCache() {
        NexaScriptCompiler compiler = new NexaScriptCompiler();
        String source = "return 42;";

        compiler.compile(source, "ws-a:flow:node", "ws-a");
        compiler.compile(source, "ws-b:flow:node", "ws-b");
        assertEquals(2, compiler.cachedWorkspaceScriptCount());

        compiler.clearWorkspace("ws-a");
        assertEquals(1, compiler.cachedWorkspaceScriptCount());

        CompiledScript recreated = compiler.compile(source, "ws-a:flow:node", "ws-a");
        assertEquals(2, compiler.cachedWorkspaceScriptCount());
        CompiledScript reused = compiler.compile(source, "ws-b:flow:node", "ws-b");
        assertSame(reused, compiler.compile(source, "ws-b:flow:node", "ws-b"));
        assertNotSame(recreated, reused);
    }
}
