package nexa.plugin.asset;

import nexa.plugin.asset.resource.AssetManagerResourcePlugin;
import nexa.plugin.asset.script.AssetScriptingEngine;
import nexa.plugin.asset.script.CompiledAssetScript;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public final class AssetScriptingEngineTest {

    @Test
    public void testCompiledScriptIsCachedPerAssetManager() {
        AssetManagerResourcePlugin manager = new AssetManagerResourcePlugin();
        AssetScriptingEngine engine = manager.getScriptingEngine();

        CompiledAssetScript first = engine.compile("return 1 + 2;");
        CompiledAssetScript second = engine.compile("return 1 + 2;");

        assertSame(first, second);
        assertEquals(1, engine.compiledScriptCount());
    }

    @Test
    public void testEachAssetManagerOwnsItsOwnEngineAndCache() {
        AssetManagerResourcePlugin firstManager = new AssetManagerResourcePlugin();
        AssetManagerResourcePlugin secondManager = new AssetManagerResourcePlugin();

        AssetScriptingEngine firstEngine = firstManager.getScriptingEngine();
        AssetScriptingEngine secondEngine = secondManager.getScriptingEngine();

        assertNotSame(firstEngine, secondEngine);

        firstEngine.compile("return 1 + 2;");

        assertEquals(1, firstEngine.compiledScriptCount());
        assertEquals(0, secondEngine.compiledScriptCount());
    }
}
