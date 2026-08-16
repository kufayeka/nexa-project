package nexa.plugin.asset.script;

import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.scripting.api.ScriptExecutionControl;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaParser;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaProgram;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaRuntime;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaTokenizer;
import nexa.plugin.asset.resource.AssetManagerResourcePlugin;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Asset Manager-owned scripting engine.
 *
 * The engine owns compilation, compiled-script caching, and per-execution
 * context. It deliberately does not expose the generic runtime's scripting
 * state to the Asset Manager itself.
 */
public final class AssetScriptingEngine {
    private final AssetManagerResourcePlugin assetManager;
    private final ConcurrentMap<String, CompiledAssetScript> compiledScripts = new ConcurrentHashMap<>();
    private final ThreadLocal<AssetScriptContext> currentContext = new ThreadLocal<>();

    public AssetScriptingEngine(AssetManagerResourcePlugin assetManager) {
        this.assetManager = assetManager;
    }

    public CompiledAssetScript compile(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Asset calculation script cannot be empty.");
        }

        return compiledScripts.computeIfAbsent(source, this::compileUncached);
    }

    private CompiledAssetScript compileUncached(String source) {
        NexaTokenizer tokenizer = new NexaTokenizer(source);
        NexaParser parser = new NexaParser(tokenizer.tokenize());
        NexaProgram program = parser.parseProgram();
        return new CompiledAssetScript(source, program);
    }

    public Object executeCalculation(
        String script,
        String attributePath,
        Object currentValue,
        Object oldValue,
        Object newValue
    ) {
        CompiledAssetScript compiled = compile(script);
        AssetScriptContext context = new AssetScriptContext(
            attributePath,
            currentValue,
            oldValue,
            newValue,
            System.currentTimeMillis(),
            "GOOD"
        );

        currentContext.set(context);
        try {
            ScriptExecutionControl control = new ScriptExecutionControl((port, msg) -> {});
            NexaRuntime runtime = new NexaRuntime(new RuntimeMessage(), control);

            Object result = executeProgram(runtime, compiled.program());
            assetManager.registerDependencies(attributePath, context.trackedReads());
            return result;
        } catch (Exception e) {
            throw new RuntimeException(
                "Error executing asset calculation script for " + attributePath + ": " + e.getMessage(),
                e
            );
        } finally {
            currentContext.remove();
        }
    }

    private Object executeProgram(NexaRuntime runtime, NexaProgram program) throws Exception {
        try {
            runtime.executeStatements(program.statements());
            return null;
        } catch (RuntimeException e) {
            if (!e.getClass().getSimpleName().equals("ReturnSignal")) {
                throw e;
            }

            Method valueMethod = e.getClass().getDeclaredMethod("value");
            valueMethod.setAccessible(true);
            return valueMethod.invoke(e);
        }
    }

    public AssetScriptContext currentContext() {
        return currentContext.get();
    }

    public int compiledScriptCount() {
        return compiledScripts.size();
    }

    public void clearCompiledScripts() {
        compiledScripts.clear();
    }
}
