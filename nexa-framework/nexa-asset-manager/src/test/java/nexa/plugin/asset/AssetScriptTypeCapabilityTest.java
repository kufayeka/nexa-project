package nexa.plugin.asset;

import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.plugin.asset.model.AssetDataType;
import nexa.plugin.asset.model.Attribute;
import nexa.plugin.asset.resource.AssetManagerResourcePlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class AssetScriptTypeCapabilityTest {
    private AssetManagerResourcePlugin plugin;
    private File configFile;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        plugin = new AssetManagerResourcePlugin();
        configFile = tempDir.resolve("empty-workspace.json").toFile();
        Files.writeString(configFile, "{\"id\":\"type-capability\",\"templates\":[],\"assets\":[]}");

        plugin.onInit("type-test", Map.of("configFile", configFile.getAbsolutePath()), new NexaPluginContext() {
            @Override public Object getSharedResource(String resourceId) { return null; }
            @Override public boolean validateScript(String language, String script, Map<String, Object> errorContainer) { return true; }
        });

        plugin.onStart();
    }

    @AfterEach
    void tearDown() {
        plugin.onStop();
    }

    @Test
    void nexaCanReadManipulateAndWriteEveryCanonicalType() {
        Map<AssetDataType, Object> values = new LinkedHashMap<>();
        values.put(AssetDataType.BOOLEAN, true);
        values.put(AssetDataType.INT8, (byte) 10);
        values.put(AssetDataType.INT16, (short) 1000);
        values.put(AssetDataType.INT32, 100_000);
        values.put(AssetDataType.INT64, 5_000_000_000L);
        values.put(AssetDataType.UINT8, 200);
        values.put(AssetDataType.UINT16, 60_000);
        values.put(AssetDataType.UINT32, 4_000_000_000L);
        values.put(AssetDataType.UINT64, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.valueOf(5)));
        values.put(AssetDataType.FLOAT32, 12.5f);
        values.put(AssetDataType.FLOAT64, 123.456d);
        values.put(AssetDataType.STRING, "nexa");
        values.put(AssetDataType.ARRAY, List.of(1, 2, 3));
        values.put(AssetDataType.OBJECT, new LinkedHashMap<>(Map.of("value", 42, "ok", true)));

        int index = 0;
        for (Map.Entry<AssetDataType, Object> entry : values.entrySet()) {
            String suffix = String.valueOf(index++);
            String sourcePath = "/source" + suffix;
            String targetPath = "/target" + suffix;
            Attribute source = new Attribute("source" + suffix, sourcePath, entry.getKey().name(), entry.getValue(), null);
            Attribute target = new Attribute("target" + suffix, targetPath, entry.getKey().name(), entry.getValue(), null);
            plugin.getFlatAttributes().put(sourcePath, source);
            plugin.getFlatAttributes().put(targetPath, target);

            String script = switch (entry.getKey()) {
                case BOOLEAN -> "assetManager.write(\"../target" + suffix + "\", !self.value); return !self.value;";
                case INT8, INT16, INT32, INT64, UINT8, UINT16, UINT32 ->
                    "assetManager.write(\"../target" + suffix + "\", self.value + 1); return self.value + 1;";
                case UINT64 ->
                    "assetManager.write(\"../target" + suffix + "\", UInt64.add(self.value, 1)); return UInt64.add(self.value, 1);";
                case FLOAT32, FLOAT64 ->
                    "assetManager.write(\"../target" + suffix + "\", self.value * 2); return self.value * 2;";
                case STRING ->
                    "assetManager.write(\"../target" + suffix + "\", self.value.toUpperCase()); return self.value.toUpperCase();";
                case ARRAY ->
                    "var a = self.value; a.push(4); assetManager.write(\"../target" + suffix + "\", a); return a.length;";
                case OBJECT ->
                    "var o = self.value; o.value += 1; assetManager.write(\"../target" + suffix + "\", o); return o.value;";
            };

            Object result = plugin.getScriptingEngine().executeCalculation(
                    script, sourcePath, source.getValue(), null, null);

            Object targetValue = plugin.read(targetPath);
            assertNotNull(targetValue, entry.getKey().name());

            switch (entry.getKey()) {
                case BOOLEAN -> assertEquals(false, targetValue);
                case INT8 -> assertEquals((byte) 11, targetValue);
                case INT16 -> assertEquals((short) 1001, targetValue);
                case INT32 -> assertEquals(100001, targetValue);
                case INT64 -> assertEquals(5_000_000_001L, targetValue);
                case UINT8 -> assertEquals(201, targetValue);
                case UINT16 -> assertEquals(60001, targetValue);
                case UINT32 -> assertEquals(4_000_000_001L, targetValue);
                case UINT64 -> assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.valueOf(4)), targetValue);
                case FLOAT32 -> assertEquals(25.0f, (Float) targetValue, 0.0001f);
                case FLOAT64 -> assertEquals(246.912d, (Double) targetValue, 0.0000001d);
                case STRING -> assertEquals("NEXA", targetValue);
                case ARRAY -> assertEquals(List.of(1, 2, 3, 4), targetValue);
                case OBJECT -> assertEquals(43, ((Map<?, ?>) targetValue).get("value"));
            }

            assertNotNull(result);
        }
    }

    @Test
    void selfExposesCurrentOldNewTimestampAndQuality() {
        Attribute attr = new Attribute("value", "/value", "INT32", 10, null);
        plugin.getFlatAttributes().put("/value", attr);
        attr.updateValue(20, "GOOD");
        attr.setNewValue(30);

        Object result = plugin.getScriptingEngine().executeCalculation(
                "return [self.value, self.oldValue, self.newValue, self.timestamp, self.quality];",
                "/value",
                attr.getValue(),
                attr.getOldValue(),
                attr.getNewValue()
        );

        assertInstanceOf(List.class, result);
        List<?> values = (List<?>) result;
        assertEquals(20, values.get(0));
        assertEquals(10, values.get(1));
        assertEquals(30, values.get(2));
        assertInstanceOf(Long.class, values.get(3));
        assertEquals("GOOD", values.get(4));
    }
}
