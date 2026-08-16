package nexa.plugin.asset;

import nexa.plugin.asset.script.AssetScriptingEngine;
import nexa.plugin.asset.script.CompiledAssetScript;
import nexa.plugin.asset.resource.AssetManagerResourcePlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public final class AssetScriptingEngineTest {

    @Test
    public void testCompiledScriptIsCachedPerAssetManager() {
        AssetManagerResourcePlugin manager = new AssetManagerResourcePlugin();
        AssetScriptingEngine engine = AssetScriptingEngine.forManager(manager);
        engine.clearCompiledScripts();

        CompiledAssetScript first = engine.compile("return 1 + 2;");
        CompiledAssetScript second = engine.compile("return 1 + 2;");

        assertSame(first, second);
        assertEquals(1, engine.compiledScriptCount());
    }
}
