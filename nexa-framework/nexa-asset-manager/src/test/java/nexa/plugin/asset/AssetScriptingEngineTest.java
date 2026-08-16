package nexa.plugin.asset;

import nexa.plugin.asset.resource.AssetManagerResourcePlugin;
import nexa.plugin.asset.script.AssetScriptingEngine;
import nexa.plugin.asset.script.CompiledAssetScript;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    public void testSelfContextCurrentOldNew() {
        AssetManagerResourcePlugin manager = new AssetManagerResourcePlugin();
        AssetScriptingEngine engine = manager.getScriptingEngine();

        Object result = engine.executeCalculation(
            "return self.value + self.oldValue + self.newValue;",
            "/Bench/value",
            2.0,
            3.0,
            4.0
        );

        assertEquals(9.0, ((Number) result).doubleValue(), 0.000001);
        assertEquals(1, engine.compiledScriptCount());
    }

    @Test
    public void testSelfContextSupportsStringBooleanListAndObjectValues() {
        AssetManagerResourcePlugin manager = new AssetManagerResourcePlugin();
        AssetScriptingEngine engine = manager.getScriptingEngine();

        Object stringResult = engine.executeCalculation(
            "return self.newValue.toUpperCase();",
            "/Bench/string",
            "old",
            "previous",
            "running"
        );
        assertEquals("RUNNING", stringResult);

        Object booleanValue = Boolean.TRUE;
        Object booleanResult = engine.executeCalculation(
            "return self.newValue;",
            "/Bench/boolean",
            Boolean.FALSE,
            Boolean.FALSE,
            booleanValue
        );
        assertSame(booleanValue, booleanResult);

        List<Integer> listValue = List.of(10, 20, 30);
        Object listResult = engine.executeCalculation(
            "return self.newValue;",
            "/Bench/list",
            List.of(1),
            List.of(2),
            listValue
        );
        assertSame(listValue, listResult);

        Map<String, Object> objectValue = Map.of("state", "RUNNING", "rpm", 1450);
        Object objectResult = engine.executeCalculation(
            "return self.newValue;",
            "/Bench/object",
            Map.of("state", "STOPPED"),
            Map.of("state", "IDLE"),
            objectValue
        );
        assertSame(objectValue, objectResult);
    }

    @Test
    public void testOneCompiledScriptReadsSeveralTagsAcrossManyAttributes() throws Exception {
        final int calculatedTags = 100;
        final int sourceTagsPerCalculation = 5;
        final String script = "return assetManager.read(\"../source0\") + "
            + "assetManager.read(\"../source1\") + "
            + "assetManager.read(\"../source2\") + "
            + "assetManager.read(\"../source3\") + "
            + "assetManager.read(\"../source4\");";

        AssetManagerResourcePlugin manager = new AssetManagerResourcePlugin();
        Path config = Files.createTempFile("nexa-asset-script-hardcore-", ".json");
        try {
            Files.writeString(config, buildWorkspaceJson(calculatedTags, sourceTagsPerCalculation, script));
            manager.onInit("hardcore-test", Map.of("configFile", config.toString()), null);

            AssetScriptingEngine engine = manager.getScriptingEngine();
            assertEquals(0, engine.compiledScriptCount());

            for (int i = 0; i < calculatedTags; i++) {
                String path = "/Bench/Tag" + i + "/calculated";
                Object result = engine.executeCalculation(script, path, 0.0, 0.0, null);
                assertEquals(15.0, ((Number) result).doubleValue(), 0.000001);
            }

            assertEquals(1, engine.compiledScriptCount());
        } finally {
            manager.onStop();
            Files.deleteIfExists(config);
        }
    }

    @Test
    public void testHundredMillisecondTagCycleBudget() throws Exception {
        final int calculatedTags = 100;
        final int sourceTagsPerCalculation = 5;
        final int warmupCycles = 10;
        final int measuredCycles = 30;
        final long budgetNanos = 100_000_000L;
        final String script = "return assetManager.read(\"../source0\") * 1.1 "
            + "+ assetManager.read(\"../source1\") * 1.2 "
            + "+ assetManager.read(\"../source2\") * 1.3 "
            + "+ assetManager.read(\"../source3\") * 1.4 "
            + "+ assetManager.read(\"../source4\") * 1.5;";

        AssetManagerResourcePlugin manager = new AssetManagerResourcePlugin();
        Path config = Files.createTempFile("nexa-asset-script-100ms-", ".json");
        try {
            Files.writeString(config, buildWorkspaceJson(calculatedTags, sourceTagsPerCalculation, script));
            manager.onInit("100ms-test", Map.of("configFile", config.toString()), null);

            AssetScriptingEngine engine = manager.getScriptingEngine();
            List<String> paths = new ArrayList<>(calculatedTags);
            for (int i = 0; i < calculatedTags; i++) {
                paths.add("/Bench/Tag" + i + "/calculated");
            }

            for (int cycle = 0; cycle < warmupCycles; cycle++) {
                for (String path : paths) {
                    Object result = engine.executeCalculation(script, path, 0.0, 0.0, null);
                    assertEquals(19.5, ((Number) result).doubleValue(), 0.000001);
                }
            }

            assertEquals(1, engine.compiledScriptCount());

            long[] cycleTimes = new long[measuredCycles];
            long totalNanos = 0L;
            for (int cycle = 0; cycle < measuredCycles; cycle++) {
                long start = System.nanoTime();
                for (String path : paths) {
                    Object result = engine.executeCalculation(script, path, 0.0, 0.0, null);
                    assertEquals(19.5, ((Number) result).doubleValue(), 0.000001);
                }
                long elapsed = System.nanoTime() - start;
                cycleTimes[cycle] = elapsed;
                totalNanos += elapsed;
            }

            java.util.Arrays.sort(cycleTimes);
            long p50 = cycleTimes[measuredCycles / 2];
            long p95 = cycleTimes[(int) Math.ceil(measuredCycles * 0.95) - 1];
            long p99 = cycleTimes[(int) Math.ceil(measuredCycles * 0.99) - 1];
            long max = cycleTimes[measuredCycles - 1];
            double avgMs = totalNanos / (double) measuredCycles / 1_000_000.0;
            double p50Ms = p50 / 1_000_000.0;
            double p95Ms = p95 / 1_000_000.0;
            double p99Ms = p99 / 1_000_000.0;
            double maxMs = max / 1_000_000.0;
            double tagsPerSecond = calculatedTags * (measuredCycles * 1_000_000_000.0) / totalNanos;

            System.out.printf(Locale.ROOT,
                "ASSET SCRIPT BENCHMARK: tags=%d, readsPerScript=%d, compiledScripts=%d, avg=%.3fms, p50=%.3fms, p95=%.3fms, p99=%.3fms, max=%.3fms, throughput=%.0f tags/s, budget=100.000ms%n",
                calculatedTags, sourceTagsPerCalculation, engine.compiledScriptCount(),
                avgMs, p50Ms, p95Ms, p99Ms, maxMs, tagsPerSecond);

            assertTrue(p95 <= budgetNanos,
                String.format(Locale.ROOT,
                    "100ms tag-cycle budget exceeded: p95=%.3fms, max=%.3fms for %d calculated tags",
                    p95Ms, maxMs, calculatedTags));
        } finally {
            manager.onStop();
            Files.deleteIfExists(config);
        }
    }

    private static String buildWorkspaceJson(int calculatedTags, int sourceTagsPerCalculation, String script) {
        String escapedScript = script.replace("\\", "\\\\").replace("\"", "\\\"");
        StringBuilder json = new StringBuilder();
        json.append("{\"id\":\"hardcore\",\"templates\":[],\"assets\":[");

        for (int i = 0; i < calculatedTags; i++) {
            if (i > 0) json.append(',');
            json.append("{\"name\":\"Tag").append(i).append("\",\"attributes\":[");

            for (int source = 0; source < sourceTagsPerCalculation; source++) {
                if (source > 0) json.append(',');
                json.append("{\"name\":\"source").append(source)
                    .append("\",\"dataType\":\"FLOAT64\",\"value\":3.0}");
            }

            json.append(",\"calculated\":{\"name\":\"calculated\",\"dataType\":\"FLOAT64\",\"value\":0.0,\"calculationConfig\":{")
                .append("\"triggerType\":\"ON_CHANGE\",\"script\":\"")
                .append(escapedScript)
                .append("\"}}");
            json.append("]}");
        }

        json.append("]}");
        return json.toString();
    }
}
