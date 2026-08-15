package nexa.framework.runtime.api.model;

import nexa.framework.runtime.api.helpers.DeepCopyUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RuntimeMessage {

    private final ConcurrentMap<String, Object> values;

    public RuntimeMessage() {
        this.values = new ConcurrentHashMap<>();
    }

    public RuntimeMessage(Map<String, Object> values) {
        this.values = new ConcurrentHashMap<>();
        if (values != null) {
            putAll(values);
        }
    }

    protected RuntimeMessage(ConcurrentMap<String, Object> values, boolean copyValues) {
        this.values = copyValues ? new ConcurrentHashMap<>() : values;
        if (copyValues) {
            putAll(values);
        }
    }

    public static RuntimeMessage shared(ConcurrentMap<String, Object> values) {
        return new RuntimeMessage(values, false);
    }

    public ConcurrentMap<String, Object> values() {
        return values;
    }

    public Object readRawValue(String path) {
        Object value = readRaw(path, false);
        return value == MissingValue.INSTANCE ? null : value;
    }

    public <T> T readValue(String path, Class<T> expectedType) {
        Objects.requireNonNull(expectedType, "expectedType must not be null");

        Object value = readRawValue(path);
        if (value == null) {
            throw new RuntimeException(formatTypeError(path, expectedType, null));
        }

        if (!expectedType.isInstance(value)) {
            throw new RuntimeException(formatTypeError(path, expectedType, value.getClass()));
        }

        return expectedType.cast(value);
    }

    public void writeValue(String path, Object value) {
        String[] segments = parsePath(path);
        ConcurrentMap<String, Object> current = values;

        for (int index = 0; index < segments.length - 1; index++) {
            String segment = segments[index];
            Object next = current.get(segment);

            if (next == null) {
                ConcurrentMap<String, Object> nested = new ConcurrentHashMap<>();
                Object previous = current.putIfAbsent(segment, nested);
                current = previous instanceof ConcurrentMap<?, ?> map
                        ? castConcurrentMap(map)
                        : nested;
                continue;
            }

            if (next instanceof ConcurrentMap<?, ?> map) {
                current = castConcurrentMap(map);
                continue;
            }

            throw new RuntimeException("Path " + joinPrefix(segments, index)
                    + " is not an object and cannot contain " + path);
        }

        current.put(segments[segments.length - 1], normalizeValue(value));
    }

    public boolean containsPath(String path) {
        return readRaw(path, false) != MissingValue.INSTANCE;
    }

    public Object removeValue(String path) {
        String[] segments = parsePath(path);
        ConcurrentMap<String, Object> current = values;

        for (int index = 0; index < segments.length - 1; index++) {
            Object next = current.get(segments[index]);
            if (!(next instanceof ConcurrentMap<?, ?> map)) {
                return null;
            }
            current = castConcurrentMap(map);
        }

        return current.remove(segments[segments.length - 1]);
    }

    public RuntimeMessage deepCopy() {
        return new RuntimeMessage(DeepCopyUtil.deepCopyMap(values));
    }

    private void putAll(Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            values.put(entry.getKey(), normalizeValue(entry.getValue()));
        }
    }

    private Object readRaw(String path, boolean throwIfMissing) {
        String[] segments = parsePath(path);
        ConcurrentMap<String, Object> current = values;

        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            Object value = current.get(segment);

            if (value == null) {
                return throwIfMissing ? MissingValue.INSTANCE : MissingValue.INSTANCE;
            }

            if (index == segments.length - 1) {
                return value;
            }

            if (!(value instanceof ConcurrentMap<?, ?> map)) {
                return throwIfMissing ? MissingValue.INSTANCE : MissingValue.INSTANCE;
            }

            current = castConcurrentMap(map);
        }

        return MissingValue.INSTANCE;
    }

    private String[] parsePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path must not be blank");
        }

        String[] segments = path.split("\\.");
        if (segments.length == 0) {
            throw new IllegalArgumentException("Path must not be blank");
        }

        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                throw new IllegalArgumentException("Path must not contain blank segments: " + path);
            }
        }

        return segments;
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<String, Object> castConcurrentMap(ConcurrentMap<?, ?> source) {
        return (ConcurrentMap<String, Object>) source;
    }

    private String joinPrefix(String[] segments, int lastIncludedIndex) {
        List<String> prefix = new ArrayList<>(lastIncludedIndex + 1);
        for (int index = 0; index <= lastIncludedIndex; index++) {
            prefix.add(segments[index]);
        }
        return String.join(".", prefix);
    }

    private String formatTypeError(String path, Class<?> expectedType, Class<?> actualType) {
        String actualTypeName = actualType == null ? "null" : actualType.getSimpleName();
        return "Expected " + expectedType.getSimpleName() + " Found " + actualTypeName + " Path " + path;
    }

    private Object normalizeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character) {
            return value;
        }

        if (value instanceof Map<?, ?> map) {
            ConcurrentMap<String, Object> normalized = new ConcurrentHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
            }
            return normalized;
        }

        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalizeValue(item));
            }
            return normalized;
        }

        return DeepCopyUtil.deepCopyValue(value);
    }

    private enum MissingValue {
        INSTANCE
    }
}
