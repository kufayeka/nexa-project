package nexa.plugin.asset;

import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.plugin.asset.model.Asset;
import nexa.plugin.asset.model.Attribute;
import nexa.plugin.asset.resource.AssetManagerResourcePlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public final class AssetManagerTest {

    private AssetManagerResourcePlugin plugin;
    private File tempConfigFile;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) throws Exception {
        plugin = new AssetManagerResourcePlugin();

        // Prepare test asset workspace configuration JSON
        String jsonConfig = """
        {
          "id": "workspace-assets-test",
          "templates": [
            {
              "name": "MotorTemplate",
              "attributes": [
                {
                  "name": "temperature",
                  "dataType": "FLOAT32",
                  "value": 20.0
                },
                {
                  "name": "tempFahrenheit",
                  "dataType": "FLOAT32",
                  "value": 32.0,
                  "calculationConfig": {
                    "triggerType": "ON_CHANGE",
                    "script": "return assetManager.read(\\"../temperature\\") * 1.8 + 32.0;"
                  }
                },
                {
                  "name": "runHours",
                  "dataType": "FLOAT64",
                  "value": 0.0,
                  "calculationConfig": {
                    "triggerType": "INTERVAL",
                    "intervalExpr": "200ms",
                    "script": "return self.value + 1.0;"
                  }
                },
                {
                  "name": "status",
                  "dataType": "STRING",
                  "value": "STOPPED",
                  "calculationConfig": {
                    "triggerType": "ON_WRITE",
                    "script": "return self.newValue.toUpperCase();"
                  }
                }
              ]
            }
          ],
          "assets": [
            {
              "name": "SiteA",
              "children": [
                {
                  "name": "Line1",
                  "children": [
                    {
                      "name": "Motor1",
                      "template": "MotorTemplate",
                      "attributes": [
                        {
                          "name": "location",
                          "dataType": "STRING",
                          "value": "Section-102"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;

        tempConfigFile = tempDir.resolve("workspace-assets.json").toFile();
        Files.writeString(tempConfigFile.toPath(), jsonConfig);

        // Initialize plugin
        plugin.onInit("test-id", Map.of("configFile", tempConfigFile.getAbsolutePath()), new NexaPluginContext() {
            @Override
            public Object getSharedResource(String resourceId) {
                return null;
            }

            @Override
            public boolean validateScript(String language, String script, Map<String, Object> errorContainer) {
                return true;
            }
        });

        // Trigger onStart
        plugin.onStart();
        
        // Wait briefly for first-time calculations to complete
        Thread.sleep(100);
    }

    @AfterEach
    public void tearDown() {
        plugin.onStop();
    }

    @Test
    public void testHierarchyAndFirstTimeEvaluation() {
        // SiteA/Line1/Motor1 is loaded
        Asset motor1 = plugin.findAssetByPath("/SiteA/Line1/Motor1");
        assertNotNull(motor1);

        // Verify first-time evaluation ran on start
        // temperature = 20.0, so tempFahrenheit should be pre-calculated to 68.0 on startup!
        assertEquals(68.0, plugin.read("/SiteA/Line1/Motor1/tempFahrenheit"));
    }

    @Test
    public void testOnChangeDependencyTriggering() {
        // Temperature starts at 20.0
        assertEquals(20.0, plugin.read("/SiteA/Line1/Motor1/temperature"));
        assertEquals(68.0, plugin.read("/SiteA/Line1/Motor1/tempFahrenheit"));

        // Writing to temperature should automatically trigger recalculation of tempFahrenheit
        // because of the dynamically registered dependency!
        plugin.write("/SiteA/Line1/Motor1/temperature", 25.0);

        assertEquals(25.0, plugin.read("/SiteA/Line1/Motor1/temperature"));
        assertEquals(77.0, plugin.read("/SiteA/Line1/Motor1/tempFahrenheit"));
    }

    @Test
    public void testOnWriteInterceptor() {
        // Status starts as "STOPPED"
        assertEquals("STOPPED", plugin.read("/SiteA/Line1/Motor1/status"));

        // Write lowercase "running"
        plugin.write("/SiteA/Line1/Motor1/status", "running");

        // The ON_WRITE trigger converts it to uppercase "RUNNING"
        assertEquals("RUNNING", plugin.read("/SiteA/Line1/Motor1/status"));
    }

    @Test
    public void testIntervalCalculationTriggering() throws Exception {
        // Initially evaluated once at startup, runHours = 1.0 (since value was 0.0 + 1.0)
        double initialVal = ((Number) plugin.read("/SiteA/Line1/Motor1/runHours")).doubleValue();
        assertTrue(initialVal >= 1.0);

        // Wait 300ms so the 200ms scheduler ticks at least once
        Thread.sleep(300);

        double updatedVal = ((Number) plugin.read("/SiteA/Line1/Motor1/runHours")).doubleValue();
        // It must have incremented by at least 1.0
        assertTrue(updatedVal > initialVal);
    }

    @Test
    public void testCircularDependencyCyclePrevention() {
        // Set up circular dependency: A depends on B, and B depends on A
        plugin.registerDependencies("/SiteA/Line1/Motor1/temperature", java.util.Set.of("/SiteA/Line1/Motor1/tempFahrenheit"));
        plugin.registerDependencies("/SiteA/Line1/Motor1/tempFahrenheit", java.util.Set.of("/SiteA/Line1/Motor1/temperature"));

        // Try to trigger recalculation, it should print cycle error and complete without StackOverflow
        assertDoesNotThrow(() -> {
            plugin.triggerDependents("/SiteA/Line1/Motor1/temperature");
        });
    }

    @Test
    public void testAssetReadInputNode() throws Exception {
        nexa.plugin.asset.node.AssetReadInputPlugin readNode = new nexa.plugin.asset.node.AssetReadInputPlugin();
        readNode.onInit("read-test-id", Map.of(
            "attributePath", "/SiteA/Line1/Motor1/temperature",
            "fireMode", "ON_CHANGE",
            "outputMode", "VALUE"
        ), null);

        java.util.List<nexa.framework.runtime.api.model.RuntimeMessage> emitted = new java.util.ArrayList<>();
        readNode.setEmitter(emitted::add);
        readNode.onStart();

        // Write a new value to trigger change event
        plugin.write("/SiteA/Line1/Motor1/temperature", 28.0);

        assertEquals(1, emitted.size());
        assertEquals(28.0, emitted.get(0).readRawValue("payload.value"));

        readNode.onStop();
    }

    @Test
    public void testAssetWriteSinkNode() throws Exception {
        nexa.plugin.asset.node.AssetWriteSinkPlugin writeNode = new nexa.plugin.asset.node.AssetWriteSinkPlugin();
        writeNode.onInit("write-test-id", Map.of(
            "attributePath", "/SiteA/Line1/Motor1/temperature",
            "valueSource", "payload.newValue"
        ), null);
        writeNode.onStart();

        nexa.framework.runtime.api.model.RuntimeMessage msg = new nexa.framework.runtime.api.model.RuntimeMessage();
        msg.writeValue("payload.newValue", 35.0);

        writeNode.consume(msg);
        assertEquals(35.0, plugin.read("/SiteA/Line1/Motor1/temperature"));

        writeNode.onStop();
    }
}
