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
 * Scripting engine owned by one Asset Manager instance.
 *
 * Source is compiled during workspace load and the immutable program is reused
 * for every execution. The hot path never tokenizes or parses a script.
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

    /** Compile a script eagerly and associate compilation failures with its asset path. */
    public CompiledAssetScript precompile(String attributePath, String source) {
        try {
            return compile(source);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                "Invalid Nexa calculation script for asset " + attributePath + ": " + exception.getMessage(),
                exception
            );
        }
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
            return extractReturnValue(e);
        }
    }

    private static volatile Method returnValueMethod;

    private static Object extractReturnValue(RuntimeException signal) throws Exception {
        Method method = returnValueMethod;
        if (method == null) {
            synchronized (AssetScriptingEngine.class) {
                method = returnValueMethod;
                if (method == null) {
                    method = signal.getClass().getDeclaredMethod("value");
                    method.setAccessible(true);
                    returnValueMethod = method;
                }
            }
        }
        return method.invoke(signal);
    }

    public AssetScriptContext currentContext() {
        return currentContext.get();
    }

    public boolean isExecuting() {
        return currentContext.get() != null;
    }

    public int compiledScriptCount() {
        return compiledScripts.size();
    }

    public void clearCompiledScripts() {
        compiledScripts.clear();
    }
}
