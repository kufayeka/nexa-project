package nexa.plugin.asset;

import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.plugin.asset.resource.AssetManagerResourcePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AssetWorkspaceCompilationTest {

    @Test
    void allCalculationScriptsAreCompiledDuringInit(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("asset-workspace.json");
        Files.writeString(config, """
            {
              "id": "compile-test",
              "templates": [],
              "assets": [
                {
                  "name": "machine",
                  "attributes": [
                    {"name": "a", "dataType": "INT32", "value": 1,
                     "calculationConfig": {"triggerType": "ON_CHANGE", "script": "return self.value + 1;"}},
                    {"name": "b", "dataType": "INT32", "value": 2,
                     "calculationConfig": {"triggerType": "INTERVAL", "intervalExpr": "1s", "script": "return self.value + 1;"}}
                  ]
                }
              ]
            }
            """);

        AssetManagerResourcePlugin plugin = new AssetManagerResourcePlugin();
        plugin.onInit("compile-test", Map.of("configFile", config.toString()), context());

        assertEquals(2, plugin.getScriptingEngine().compiledScriptCount());
        plugin.onStop();
    }

    @Test
    void invalidCalculationScriptPreventsAssetWorkspaceInitialization(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("invalid-asset-workspace.json");
        Files.writeString(config, """
            {
              "id": "invalid-compile-test",
              "templates": [],
              "assets": [
                {
                  "name": "machine",
                  "attributes": [
                    {"name": "bad", "dataType": "INT32", "value": 1,
                     "calculationConfig": {"triggerType": "ON_CHANGE", "script": "return ; ; ;"}}
                  ]
                }
              ]
            }
            """);

        AssetManagerResourcePlugin plugin = new AssetManagerResourcePlugin();
        assertThrows(IllegalArgumentException.class,
            () -> plugin.onInit("invalid-compile-test", Map.of("configFile", config.toString()), context()));
        plugin.onStop();
    }

    private static NexaPluginContext context() {
        return new NexaPluginContext() {
            @Override public Object getSharedResource(String resourceId) { return null; }
            @Override public boolean validateScript(String language, String script, Map<String, Object> errorContainer) { return true; }
        };
    }
}
