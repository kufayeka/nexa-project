package nexa.plugin.asset.script;

import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.scripting.api.ScriptExecutionControl;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaParser;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaProgram;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaRuntime;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaTokenizer;
import nexa.plugin.asset.resource.AssetManagerResourcePlugin;

import java.lang.reflect.Method;

public final class AssetScriptExecutor {

    public static Object executeCalculation(String script, String attributePath, Object currentValue, Object oldValue, Object newValue) {
        try {
            ScriptContextTracker.setContextPath(attributePath);
            AssetManagerResourcePlugin.setInsideCalculationScript(true);
            ScriptContextTracker.startTrackingReads();
            
            ScriptSelfContext.setContext(new ScriptSelfContext.Self(
                currentValue, 
                oldValue, 
                newValue, 
                System.currentTimeMillis(), 
                "GOOD"
            ));

            NexaTokenizer tokenizer = new NexaTokenizer(script);
            NexaParser parser = new NexaParser(tokenizer.tokenize());
            NexaProgram program = parser.parseProgram();

            ScriptExecutionControl control = new ScriptExecutionControl((port, msg) -> {});
            NexaRuntime runtime = new NexaRuntime(new RuntimeMessage(), control);

            Object resultVal = null;
            try {
                runtime.executeStatements(program.statements());
            } catch (RuntimeException e) {
                if (e.getClass().getSimpleName().equals("ReturnSignal")) {
                    Method valueMethod = e.getClass().getDeclaredMethod("value");
                    valueMethod.setAccessible(true);
                    resultVal = valueMethod.invoke(e);
                } else {
                    throw e;
                }
            }

            AssetManagerResourcePlugin manager = AssetManagerResourcePlugin.getActiveInstance();
            if (manager != null) {
                manager.registerDependencies(attributePath, ScriptContextTracker.getTrackedReads());
            }

            return resultVal;
        } catch (Exception e) {
            throw new RuntimeException("Error executing asset calculation script for " + attributePath + ": " + e.getMessage(), e);
        } finally {
            ScriptContextTracker.stopTrackingReads();
            ScriptContextTracker.clearContext();
            AssetManagerResourcePlugin.setInsideCalculationScript(false);
            ScriptSelfContext.clearContext();
        }
    }
}
