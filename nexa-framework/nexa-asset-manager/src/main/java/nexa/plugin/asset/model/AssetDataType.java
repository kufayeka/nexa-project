package nexa.plugin.asset.model;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/** Canonical value types supported by the Asset/Tag system. */
public enum AssetDataType {
    BOOLEAN,
    INT8,
    INT16,
    INT32,
    INT64,
    UINT8,
    UINT16,
    UINT32,
    UINT64,
    FLOAT32,
    FLOAT64,
    STRING,
    ARRAY,
    OBJECT;

    public static AssetDataType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Asset attribute dataType is required.");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported Asset dataType: " + value
                    + ". Supported types: " + java.util.Arrays.toString(values()), e);
        }
    }

    public Object coerce(Object value) {
        if (value == null) {
            return null;
        }
        return switch (this) {
            case BOOLEAN -> requireBoolean(value);
            case INT8 -> Byte.valueOf(requireIntegral(value).byteValueExact());
            case INT16 -> Short.valueOf(requireIntegral(value).shortValueExact());
            case INT32 -> Integer.valueOf(requireIntegral(value).intValueExact());
            case INT64 -> Long.valueOf(requireIntegral(value).longValueExact());
            case UINT8 -> Integer.valueOf(unsignedRange(requireIntegral(value), 0, 255).intValue());
            case UINT16 -> Integer.valueOf(unsignedRange(requireIntegral(value), 0, 65_535).intValue());
            case UINT32 -> Long.valueOf(unsignedRange(requireIntegral(value), 0, 4_294_967_295L).longValue());
            case UINT64 -> unsignedRange(requireIntegral(value), BigInteger.ZERO,
                    BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE));
            case FLOAT32 -> Float.valueOf(requireNumber(value).floatValue());
            case FLOAT64 -> Double.valueOf(requireNumber(value).doubleValue());
            case STRING -> requireString(value);
            case ARRAY -> {
                if (!(value instanceof List<?>)) {
                    throw typeError(value, "ARRAY");
                }
                yield value;
            }
            case OBJECT -> {
                if (!(value instanceof Map<?, ?>)) {
                    throw typeError(value, "OBJECT");
                }
                yield value;
            }
        };
    }

    private static Boolean requireBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        throw typeError(value, "BOOLEAN");
    }

    private static String requireString(Object value) {
        if (value instanceof String s) return s;
        throw typeError(value, "STRING");
    }

    private static Number requireNumber(Object value) {
        if (value instanceof Number n) return n;
        throw typeError(value, "NUMBER");
    }

    private static BigInteger requireIntegral(Object value) {
        if (value instanceof BigInteger b) return b;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        if (value instanceof Number n) {
            try {
                return new BigInteger(n.toString());
            } catch (NumberFormatException ignored) {
                throw typeError(value, "integral number");
            }
        }
        throw typeError(value, "integral number");
    }

    private static BigInteger unsignedRange(BigInteger value, long min, long max) {
        return unsignedRange(value, BigInteger.valueOf(min), BigInteger.valueOf(max));
    }

    private static BigInteger unsignedRange(BigInteger value, BigInteger min, BigInteger max) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException("Unsigned value out of range: " + value
                    + " (expected " + min + ".." + max + ")");
        }
        return value;
    }

    private static IllegalArgumentException typeError(Object value, String expected) {
        return new IllegalArgumentException("Value of type " + value.getClass().getSimpleName()
                + " cannot be used as " + expected + ".");
    }
}
