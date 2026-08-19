package nexa.plugin.asset;

import nexa.plugin.asset.model.AssetDataType;
import nexa.plugin.asset.model.Attribute;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class AssetDataTypeTest {

    @Test
    void supportsEveryCanonicalTagType() {
        assertEquals(Boolean.TRUE, new Attribute("b", "/b", "BOOLEAN", true, null).getValue());
        assertEquals(Byte.valueOf((byte) -128), new Attribute("i8", "/i8", "INT8", -128, null).getValue());
        assertEquals(Short.valueOf((short) -32768), new Attribute("i16", "/i16", "INT16", -32768, null).getValue());
        assertEquals(Integer.valueOf(-2_147_483_648), new Attribute("i32", "/i32", "INT32", -2_147_483_648L, null).getValue());
        assertEquals(Long.valueOf(Long.MIN_VALUE), new Attribute("i64", "/i64", "INT64", Long.MIN_VALUE, null).getValue());

        assertEquals(255, new Attribute("u8", "/u8", "UINT8", 255, null).getValue());
        assertEquals(65_535, new Attribute("u16", "/u16", "UINT16", 65_535, null).getValue());
        assertEquals(4_294_967_295L, new Attribute("u32", "/u32", "UINT32", 4_294_967_295L, null).getValue());
        assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE),
                new Attribute("u64", "/u64", "UINT64", BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), null).getValue());

        assertEquals(Float.valueOf(12.5f), new Attribute("f32", "/f32", "FLOAT32", 12.5, null).getValue());
        assertEquals(Double.valueOf(12.5), new Attribute("f64", "/f64", "FLOAT64", 12.5, null).getValue());
        assertEquals("hello", new Attribute("s", "/s", "STRING", "hello", null).getValue());
        assertEquals(List.of(1, 2, 3), new Attribute("a", "/a", "ARRAY", List.of(1, 2, 3), null).getValue());
        assertEquals(Map.of("ok", true, "count", 2), new Attribute("o", "/o", "OBJECT", Map.of("ok", true, "count", 2), null).getValue());
    }

    @Test
    void enforcesIntegerAndUnsignedRanges() {
        assertThrows(IllegalArgumentException.class, () -> new Attribute("x", "/x", "INT8", 128, null));
        assertThrows(IllegalArgumentException.class, () -> new Attribute("x", "/x", "INT16", 32_768, null));
        assertThrows(IllegalArgumentException.class, () -> new Attribute("x", "/x", "INT32", 2_147_483_648L, null));
        assertThrows(IllegalArgumentException.class, () -> new Attribute("x", "/x", "UINT8", -1, null));
        assertThrows(IllegalArgumentException.class, () -> new Attribute("x", "/x", "UINT16", 65_536, null));
        assertThrows(IllegalArgumentException.class, () -> new Attribute("x", "/x", "UINT32", 4_294_967_296L, null));
        assertThrows(IllegalArgumentException.class, () -> new Attribute("x", "/x", "UINT64", BigInteger.ONE.shiftLeft(64), null));
    }

    @Test
    void validatesCompositeTypes() {
        assertThrows(IllegalArgumentException.class, () -> new Attribute("x", "/x", "ARRAY", Map.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new Attribute("x", "/x", "OBJECT", List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new Attribute("x", "/x", "BOOLEAN", 1, null));
        assertThrows(IllegalArgumentException.class, () -> new Attribute("x", "/x", "STRING", 1, null));
    }

    @Test
    void updateValueAndNewValueRemainTyped() {
        Attribute attr = new Attribute("count", "/count", "INT16", 10, null);
        attr.updateValue(20L, "GOOD");
        attr.setNewValue(30L);

        assertEquals(Short.valueOf((short) 20), attr.getValue());
        assertEquals(Short.valueOf((short) 10), attr.getOldValue());
        assertEquals(Short.valueOf((short) 30), attr.getNewValue());
        assertEquals(AssetDataType.INT16, attr.getAssetDataType());
    }
}
