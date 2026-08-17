package nexa.framework.tags;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Deployment-time path resolution + slot-based hot-path storage.
 * Reads/writes by slot are primitive-array accesses and do not allocate.
 */
public final class FastTagStore {
    private static final VarHandle INT = MethodHandles.arrayElementVarHandle(int[].class);
    private static final VarHandle LONG = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle DOUBLE = MethodHandles.arrayElementVarHandle(double[].class);
    private static final VarHandle OBJECT = MethodHandles.arrayElementVarHandle(Object[].class);

    private final List<TagDataType> types;
    private final List<String> paths;
    private final Map<String, Integer> slots = new ConcurrentHashMap<>();
    private final int[] intValues;
    private final long[] longValues;
    private final double[] doubleValues;
    private final Object[] objectValues;
    private final List<Consumer<TagValue>> writeListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<TagValue>> changeListeners = new CopyOnWriteArrayList<>();

    public FastTagStore(List<TagDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        this.types = new ArrayList<>(definitions.size());
        this.paths = new ArrayList<>(definitions.size());
        int ints = 0, longs = 0, doubles = 0, objects = 0;
        for (TagDefinition definition : definitions) {
            if (definition.name() == null || definition.name().isBlank()) {
                throw new IllegalArgumentException("Tag name must not be blank");
            }
            if (slots.putIfAbsent(definition.name(), definitions.indexOf(definition)) != null) {
                throw new IllegalArgumentException("Duplicate tag: " + definition.name());
            }
            paths.add(definition.name());
            types.add(definition.dataType());
            switch (definition.dataType()) {
                case BOOLEAN, INT8, UINT8, INT16, UINT16, INT32, UINT32 -> ints++;
                case INT64, UINT64 -> longs++;
                case FLOAT32, FLOAT64 -> doubles++;
                case STRING, OBJECT -> objects++;
            }
        }
        intValues = new int[ints];
        longValues = new long[longs];
        doubleValues = new double[doubles];
        objectValues = new Object[objects];
        initialize(definitions);
    }

    private final int[] intIndexes;
    private final int[] longIndexes;
    private final int[] doubleIndexes;
    private final int[] objectIndexes;

    private void initialize(List<TagDefinition> definitions) {
        int[] i = new int[types.size()], l = new int[types.size()], d = new int[types.size()], o = new int[types.size()];
        int ii = 0, li = 0, di = 0, oi = 0;
        for (int slot = 0; slot < types.size(); slot++) {
            switch (types.get(slot)) {
                case BOOLEAN, INT8, UINT8, INT16, UINT16, INT32, UINT32 -> i[slot] = ii++;
                case INT64, UINT64 -> l[slot] = li++;
                case FLOAT32, FLOAT64 -> d[slot] = di++;
                case STRING, OBJECT -> o[slot] = oi++;
            }
        }
        intIndexes = i; longIndexes = l; doubleIndexes = d; objectIndexes = o;
        for (int slot = 0; slot < definitions.size(); slot++) {
            Object value = definitions.get(slot).value();
            if (value != null) writeSlot(slot, value, TagQuality.GOOD, false);
        }
    }

    public int slot(String path) {
        Integer slot = slots.get(path);
        if (slot == null) throw new IllegalArgumentException("Unknown tag: " + path);
        return slot;
    }

    public Object read(String path) { return readSlot(slot(path)); }

    public Object readSlot(int slot) {
        return switch (types.get(slot)) {
            case BOOLEAN -> readInt(slot) != 0;
            case INT8, UINT8, INT16, UINT16, INT32, UINT32 -> readInt(slot);
            case INT64, UINT64 -> readLong(slot);
            case FLOAT32, FLOAT64 -> readDouble(slot);
            case STRING, OBJECT -> OBJECT.getVolatile(objectValues, objectIndexes[slot]);
        };
    }

    public int readInt(int slot) { return (int) INT.getVolatile(intValues, intIndexes[slot]); }
    public long readLong(int slot) { return (long) LONG.getVolatile(longValues, longIndexes[slot]); }
    public double readDouble(int slot) { return (double) DOUBLE.getVolatile(doubleValues, doubleIndexes[slot]); }

    public TagValue write(String path, Object value) { return writeSlot(slot(path), value, TagQuality.GOOD, true); }

    public TagValue write(String path, Object value, TagQuality quality) {
        return writeSlot(slot(path), value, quality, true);
    }

    public TagValue writeSlot(int slot, Object value, TagQuality quality, boolean notify) {
        Object old = readSlot(slot);
        store(slot, value);
        long now = System.currentTimeMillis();
        TagValue event = notify && (!writeListeners.isEmpty() || !changeListeners.isEmpty())
                ? new TagValue(paths.get(slot), value, old, value, now, quality) : null;
        if (event != null) {
            for (Consumer<TagValue> listener : writeListeners) listener.accept(event);
            if (!Objects.equals(old, value)) for (Consumer<TagValue> listener : changeListeners) listener.accept(event);
        }
        return event;
    }

    private void store(int slot, Object value) {
        switch (types.get(slot)) {
            case BOOLEAN, INT8, UINT8, INT16, UINT16, INT32, UINT32 -> INT.setVolatile(intValues, intIndexes[slot], ((Number) normalize(value, types.get(slot))).intValue());
            case INT64, UINT64 -> LONG.setVolatile(longValues, longIndexes[slot], ((Number) normalize(value, types.get(slot))).longValue());
            case FLOAT32, FLOAT64 -> DOUBLE.setVolatile(doubleValues, doubleIndexes[slot], ((Number) normalize(value, types.get(slot))).doubleValue());
            case STRING -> OBJECT.setVolatile(objectValues, objectIndexes[slot], String.valueOf(value));
            case OBJECT -> OBJECT.setVolatile(objectValues, objectIndexes[slot], value);
        }
    }

    private static Object normalize(Object value, TagDataType type) {
        if (type == TagDataType.BOOLEAN) return Boolean.TRUE.equals(value) ? 1 : 0;
        if (!(value instanceof Number)) throw new IllegalArgumentException("Tag value must be numeric for " + type);
        return value;
    }

    public void onWrite(Consumer<TagValue> listener) { writeListeners.add(Objects.requireNonNull(listener)); }
    public void onChange(Consumer<TagValue> listener) { changeListeners.add(Objects.requireNonNull(listener)); }
    public Map<String, Integer> slots() { return Map.copyOf(slots); }
}
