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
import java.util.ArrayList;
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
        Files.writeString(
                configFile.toPath(),
                "{\"id\":\"type-capability\",\"templates\":[],\"assets\":[]}"
        );

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
    void nexaCanManipulateEveryCanonicalTypeAndReturnCalculatedValue() {
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
        values.put(AssetDataType.ARRAY, new ArrayList<>(List.of(1, 2, 3)));
        values.put(AssetDataType.OBJECT, new LinkedHashMap<>(Map.of("value", 42, "ok", true)));

        int index = 0;
        for (Map.Entry<AssetDataType, Object> entry : values.entrySet()) {
            String path = "/value" + index++;
            Attribute attr = new Attribute("value" + index, path, entry.getKey().name(), entry.getValue(), null);
            plugin.getFlatAttributes().put(path, attr);

            String script = switch (entry.getKey()) {
                case BOOLEAN -> "return !self.value;";
                case INT8, INT16, INT32, INT64, UINT8, UINT16, UINT32 -> "return self.value + 1;";
                case UINT64 -> "return UInt64.add(self.value, 1);";
                case FLOAT32, FLOAT64 -> "return self.value * 2;";
                case STRING -> "return self.value.toUpperCase();";
                case ARRAY -> "var a = self.value; a.push(4); return a;";
                case OBJECT -> "var o = self.value; o.value += 1; return o;";
            };

            Object result = plugin.getScriptingEngine().executeCalculation(
                    script, path, attr.getValue(), attr.getOldValue(), attr.getNewValue()
            );

            assertNotNull(result, entry.getKey().name());
            attr.updateValue(result, "GOOD");

            Object actual = attr.getValue();
            switch (entry.getKey()) {
                case BOOLEAN -> assertEquals(false, actual);
                case INT8 -> assertEquals((byte) 11, actual);
                case INT16 -> assertEquals((short) 1001, actual);
                case INT32 -> assertEquals(100001, actual);
                case INT64 -> assertEquals(5_000_000_001L, actual);
                case UINT8 -> assertEquals(201, actual);
                case UINT16 -> assertEquals(60001, actual);
                case UINT32 -> assertEquals(4_000_000_001L, actual);
                case UINT64 -> assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.valueOf(4)), actual);
                case FLOAT32 -> assertEquals(25.0f, (Float) actual, 0.0001f);
                case FLOAT64 -> assertEquals(246.912d, (Double) actual, 0.0000001d);
                case STRING -> assertEquals("NEXA", actual);
                case ARRAY -> assertEquals(List.of(1, 2, 3, 4), actual);
                case OBJECT -> assertEquals(43, ((Map<?, ?>) actual).get("value"));
            }
        }
    }

    @Test
    void calculationContractReturnsNewSelfValue() {
        Attribute attr = new Attribute("temperature", "/temperature", "FLOAT64", 20.0d, null);
        plugin.getFlatAttributes().put("/temperature", attr);

        Object result = plugin.getScriptingEngine().executeCalculation(
                "return self.value * 1.8 + 32;",
                "/temperature",
                attr.getValue(),
                attr.getOldValue(),
                25.0d
        );

        assertEquals(68.0d, result);
        attr.updateValue(result, "GOOD");
        assertEquals(68.0d, attr.getValue());
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

    @Test
    void scriptCanWriteAnotherTagWithoutUsingSelfWrite() {
        Attribute source = new Attribute("source", "/source", "INT32", 10, null);
        Attribute target = new Attribute("target", "/target", "INT32", 0, null);
        plugin.getFlatAttributes().put("/source", source);
        plugin.getFlatAttributes().put("/target", target);

        Object result = plugin.getScriptingEngine().executeCalculation(
                "assetManager.write(\"../target\", self.value + 5); return self.value;",
                "/source",
                source.getValue(),
                source.getOldValue(),
                source.getNewValue()
        );

        assertEquals(10, result);
        assertEquals(15, plugin.read("/target"));
    }
}
