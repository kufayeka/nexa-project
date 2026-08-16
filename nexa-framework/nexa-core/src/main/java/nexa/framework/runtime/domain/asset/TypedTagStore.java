package nexa.framework.runtime.domain.asset;

import java.util.Objects;

/**
 * Typed, slot-based tag storage. Name resolution is intentionally outside the
 * hot path: compiled code should retain TagSlot instances.
 */
public final class TypedTagStore {
    private final TagStoreSchema schema;
    private final boolean[] booleans;
    private final byte[] int8;
    private final short[] int16;
    private final int[] int32;
    private final long[] int64;
    private final short[] uint8;
    private final int[] uint16;
    private final long[] uint32;
    private final long[] uint64;
    private final float[] float32;
    private final double[] float64;
    private final Object[] references;

    public TypedTagStore(TagStoreSchema schema) {
        this.schema = Objects.requireNonNull(schema, "schema");
        booleans = new boolean[schema.count(TagValueType.BOOLEAN)];
        int8 = new byte[schema.count(TagValueType.INT8)];
        int16 = new short[schema.count(TagValueType.INT16)];
        int32 = new int[schema.count(TagValueType.INT32)];
        int64 = new long[schema.count(TagValueType.INT64)];
        uint8 = new short[schema.count(TagValueType.UINT8)];
        uint16 = new int[schema.count(TagValueType.UINT16)];
        uint32 = new long[schema.count(TagValueType.UINT32)];
        uint64 = new long[schema.count(TagValueType.UINT64)];
        float32 = new float[schema.count(TagValueType.FLOAT32)];
        float64 = new double[schema.count(TagValueType.FLOAT64)];
        references = new Object[schema.count(TagValueType.STRING) + schema.count(TagValueType.OBJECT)];
    }

    public TagStoreSchema schema() { return schema; }

    public boolean getBoolean(TagSlot slot) { check(slot, TagValueType.BOOLEAN); return booleans[slot.storageIndex()]; }
    public void setBoolean(TagSlot slot, boolean value) { check(slot, TagValueType.BOOLEAN); booleans[slot.storageIndex()] = value; }
    public byte getInt8(TagSlot slot) { check(slot, TagValueType.INT8); return int8[slot.storageIndex()]; }
    public void setInt8(TagSlot slot, byte value) { check(slot, TagValueType.INT8); int8[slot.storageIndex()] = value; }
    public short getInt16(TagSlot slot) { check(slot, TagValueType.INT16); return int16[slot.storageIndex()]; }
    public void setInt16(TagSlot slot, short value) { check(slot, TagValueType.INT16); int16[slot.storageIndex()] = value; }
    public int getInt32(TagSlot slot) { check(slot, TagValueType.INT32); return int32[slot.storageIndex()]; }
    public void setInt32(TagSlot slot, int value) { check(slot, TagValueType.INT32); int32[slot.storageIndex()] = value; }
    public long getInt64(TagSlot slot) { check(slot, TagValueType.INT64); return int64[slot.storageIndex()]; }
    public void setInt64(TagSlot slot, long value) { check(slot, TagValueType.INT64); int64[slot.storageIndex()] = value; }
    public short getUInt8(TagSlot slot) { check(slot, TagValueType.UINT8); return uint8[slot.storageIndex()]; }
    public void setUInt8(TagSlot slot, short value) { check(slot, TagValueType.UINT8); uint8[slot.storageIndex()] = value; }
    public int getUInt16(TagSlot slot) { check(slot, TagValueType.UINT16); return uint16[slot.storageIndex()]; }
    public void setUInt16(TagSlot slot, int value) { check(slot, TagValueType.UINT16); uint16[slot.storageIndex()] = value; }
    public long getUInt32(TagSlot slot) { check(slot, TagValueType.UINT32); return uint32[slot.storageIndex()]; }
    public void setUInt32(TagSlot slot, long value) { check(slot, TagValueType.UINT32); uint32[slot.storageIndex()] = value; }
    public long getUInt64(TagSlot slot) { check(slot, TagValueType.UINT64); return uint64[slot.storageIndex()]; }
    public void setUInt64(TagSlot slot, long value) { check(slot, TagValueType.UINT64); uint64[slot.storageIndex()] = value; }
    public float getFloat32(TagSlot slot) { check(slot, TagValueType.FLOAT32); return float32[slot.storageIndex()]; }
    public void setFloat32(TagSlot slot, float value) { check(slot, TagValueType.FLOAT32); float32[slot.storageIndex()] = value; }
    public double getFloat64(TagSlot slot) { check(slot, TagValueType.FLOAT64); return float64[slot.storageIndex()]; }
    public void setFloat64(TagSlot slot, double value) { check(slot, TagValueType.FLOAT64); float64[slot.storageIndex()] = value; }

    public Object getReference(TagSlot slot) {
        checkReference(slot);
        return references[referenceIndex(slot)];
    }

    public void setReference(TagSlot slot, Object value) {
        checkReference(slot);
        references[referenceIndex(slot)] = value;
    }

    private int referenceIndex(TagSlot slot) {
        return slot.type() == TagValueType.STRING
                ? slot.storageIndex()
                : schema.count(TagValueType.STRING) + slot.storageIndex();
    }

    private void checkReference(TagSlot slot) {
        if (slot.type() != TagValueType.STRING && slot.type() != TagValueType.OBJECT) {
            throw new IllegalArgumentException("Not a reference tag: " + slot.name());
        }
    }

    private static void check(TagSlot slot, TagValueType expected) {
        if (slot.type() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " but slot is " + slot.type());
        }
    }
}
