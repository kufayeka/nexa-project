package nexa.plugin.modbus.helper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ModbusDataConverter {

    private ModbusDataConverter() {
    }

    /**
     * Converts a raw 16-bit register array (int[]) to a byte array.
     */
    public static byte[] registersToBytes(int[] registers) {
        if (registers == null) return new byte[0];
        byte[] bytes = new byte[registers.length * 2];
        for (int i = 0; i < registers.length; i++) {
            bytes[i * 2] = (byte) ((registers[i] >> 8) & 0xFF);
            bytes[i * 2 + 1] = (byte) (registers[i] & 0xFF);
        }
        return bytes;
    }

    /**
     * Converts a byte array back into a 16-bit register array (int[]).
     */
    public static int[] bytesToRegisters(byte[] bytes) {
        if (bytes == null) return new int[0];
        int[] regs = new int[bytes.length / 2];
        for (int i = 0; i < regs.length; i++) {
            regs[i] = ((bytes[i * 2] & 0xFF) << 8) | (bytes[i * 2 + 1] & 0xFF);
        }
        return regs;
    }

    /**
     * Applies byte/word swapping according to the selected Endianness mode.
     * Since all swaps are symmetric, this method is self-inverse.
     */
    public static byte[] applyEndianness(byte[] raw, String mode) {
        if (raw == null || mode == null) return raw;
        byte[] bytes = raw.clone();
        String normalizedMode = mode.trim().toUpperCase();

        switch (normalizedMode) {
            case "BADC" -> {
                // Byte swap: Swap adjacent bytes inside each 16-bit word
                for (int i = 0; i < bytes.length - 1; i += 2) {
                    byte temp = bytes[i];
                    bytes[i] = bytes[i + 1];
                    bytes[i + 1] = temp;
                }
            }
            case "CDAB" -> {
                // Word swap: Swap adjacent 16-bit registers (pairs of bytes)
                for (int i = 0; i < bytes.length - 3; i += 4) {
                    byte temp0 = bytes[i];
                    byte temp1 = bytes[i + 1];
                    bytes[i] = bytes[i + 2];
                    bytes[i + 1] = bytes[i + 3];
                    bytes[i + 2] = temp0;
                    bytes[i + 3] = temp1;
                }
            }
            case "DCBA" -> {
                // Double swap / Little Endian: Reverse the entire block
                for (int i = 0; i < bytes.length / 2; i++) {
                    int opposite = bytes.length - 1 - i;
                    byte temp = bytes[i];
                    bytes[i] = bytes[opposite];
                    bytes[opposite] = temp;
                }
            }
            case "ABCD" -> {
                // Big Endian (Standard): No modifications needed
            }
        }
        return bytes;
    }

    // --- DECODERS (bytes to typed values) ---

    public static short toInt16(byte[] bytes) {
        if (bytes.length < 2) return 0;
        return (short) (((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF));
    }

    public static int toUint16(byte[] bytes) {
        if (bytes.length < 2) return 0;
        return (((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF)) & 0xFFFF;
    }

    public static int toInt32(byte[] bytes) {
        if (bytes.length < 4) return 0;
        return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8)  |
               (bytes[3] & 0xFF);
    }

    public static long toUint32(byte[] bytes) {
        if (bytes.length < 4) return 0L;
        long val = (((long) (bytes[0] & 0xFF)) << 24) |
                   (((long) (bytes[1] & 0xFF)) << 16) |
                   (((long) (bytes[2] & 0xFF)) << 8)  |
                   ((long) (bytes[3] & 0xFF));
        return val & 0xFFFFFFFFL;
    }

    public static float toFloat32(byte[] bytes) {
        return Float.intBitsToFloat(toInt32(bytes));
    }

    public static long toInt64(byte[] bytes) {
        if (bytes.length < 8) return 0L;
        return ((long) (bytes[0] & 0xFF) << 56) |
               ((long) (bytes[1] & 0xFF) << 48) |
               ((long) (bytes[2] & 0xFF) << 40) |
               ((long) (bytes[3] & 0xFF) << 32) |
               ((long) (bytes[4] & 0xFF) << 24) |
               ((long) (bytes[5] & 0xFF) << 16) |
               ((long) (bytes[6] & 0xFF) << 8)  |
               ((long) (bytes[7] & 0xFF));
    }

    public static BigInteger toUint64(byte[] bytes) {
        if (bytes.length < 8) return BigInteger.ZERO;
        byte[] unsignedBytes = new byte[9]; // 1 extra leading zero byte to make it positive
        System.arraycopy(bytes, 0, unsignedBytes, 1, 8);
        return new BigInteger(unsignedBytes);
    }

    public static double toFloat64(byte[] bytes) {
        return Double.longBitsToDouble(toInt64(bytes));
    }

    public static String toString(byte[] bytes) {
        String s = new String(bytes, StandardCharsets.US_ASCII);
        int nullIndex = s.indexOf('\0');
        if (nullIndex >= 0) {
            s = s.substring(0, nullIndex);
        }
        return s.trim();
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static List<Integer> toRawIntList(int[] registers) {
        List<Integer> list = new ArrayList<>(registers.length);
        for (int r : registers) {
            list.add(r);
        }
        return list;
    }

    // --- ENCODERS (typed values to bytes) ---

    public static byte[] fromInt16(int val) {
        return new byte[] {
            (byte) ((val >> 8) & 0xFF),
            (byte) (val & 0xFF)
        };
    }

    public static byte[] fromInt32(long val) {
        return new byte[] {
            (byte) ((val >> 24) & 0xFF),
            (byte) ((val >> 16) & 0xFF),
            (byte) ((val >> 8) & 0xFF),
            (byte) (val & 0xFF)
        };
    }

    public static byte[] fromFloat32(float val) {
        return fromInt32(Float.floatToIntBits(val));
    }

    public static byte[] fromInt64(long val) {
        return new byte[] {
            (byte) ((val >> 56) & 0xFF),
            (byte) ((val >> 48) & 0xFF),
            (byte) ((val >> 40) & 0xFF),
            (byte) ((val >> 32) & 0xFF),
            (byte) ((val >> 24) & 0xFF),
            (byte) ((val >> 16) & 0xFF),
            (byte) ((val >> 8) & 0xFF),
            (byte) (val & 0xFF)
        };
    }

    public static byte[] fromUint64(BigInteger val) {
        byte[] biBytes = val.toByteArray();
        byte[] bytes = new byte[8];
        if (biBytes.length >= 8) {
            System.arraycopy(biBytes, biBytes.length - 8, bytes, 0, 8);
        } else {
            System.arraycopy(biBytes, 0, bytes, 8 - biBytes.length, biBytes.length);
        }
        return bytes;
    }

    public static byte[] fromFloat64(double val) {
        return fromInt64(Double.doubleToLongBits(val));
    }

    public static byte[] fromString(String val, int registerQty) {
        byte[] strBytes = val.getBytes(StandardCharsets.US_ASCII);
        byte[] bytes = new byte[registerQty * 2];
        System.arraycopy(strBytes, 0, bytes, 0, Math.min(strBytes.length, bytes.length));
        return bytes;
    }

    public static byte[] fromHex(String hex) {
        String cleanHex = hex.replaceAll("[^0-9A-Fa-f]", "");
        int len = cleanHex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(cleanHex.charAt(i), 16) << 4)
                                 + Character.digit(cleanHex.charAt(i+1), 16));
        }
        return bytes;
    }

    /**
     * Converts a generic object value to registers based on expected type.
     */
    public static int[] encodeValue(Object value, String type, String endianness, int registerQty) {
        if (value == null) return new int[registerQty];

        byte[] rawBytes;
        String upperType = type.trim().toUpperCase();

        switch (upperType) {
            case "INT16", "UINT16" -> {
                int intVal = ((Number) value).intValue();
                rawBytes = fromInt16(intVal);
            }
            case "INT32", "UINT32" -> {
                long longVal = ((Number) value).longValue();
                rawBytes = fromInt32(longVal);
            }
            case "FLOAT32", "FLOAT" -> {
                float floatVal = ((Number) value).floatValue();
                rawBytes = fromFloat32(floatVal);
            }
            case "INT64", "LONG" -> {
                long longVal = ((Number) value).longValue();
                rawBytes = fromInt64(longVal);
            }
            case "UINT64" -> {
                BigInteger biVal;
                if (value instanceof BigInteger bi) {
                    biVal = bi;
                } else if (value instanceof String s) {
                    biVal = new BigInteger(s);
                } else {
                    biVal = BigInteger.valueOf(((Number) value).longValue());
                }
                rawBytes = fromUint64(biVal);
            }
            case "FLOAT64", "DOUBLE" -> {
                double doubleVal = ((Number) value).doubleValue();
                rawBytes = fromFloat64(doubleVal);
            }
            case "STRING" -> {
                rawBytes = fromString(value.toString(), registerQty);
            }
            case "RAW_HEX" -> {
                rawBytes = fromHex(value.toString());
            }
            case "RAW_INT" -> {
                if (value instanceof List<?> list) {
                    int[] regs = new int[list.size()];
                    for (int i = 0; i < regs.length; i++) {
                        regs[i] = ((Number) list.get(i)).intValue();
                    }
                    return regs;
                }
                throw new IllegalArgumentException("RAW_INT requires a list of integers");
            }
            default -> throw new IllegalArgumentException("Unsupported Modbus write data type: " + type);
        }

        // Apply endianness conversion (self-inverse)
        byte[] swapped = applyEndianness(rawBytes, endianness);
        return bytesToRegisters(swapped);
    }

    /**
     * Decodes a raw register array (int[]) into a typed value based on type and endianness.
     */
    public static Object decodeValue(int[] registers, String type, String endianness) {
        if (registers == null || registers.length == 0) return null;

        String upperType = type.trim().toUpperCase();
        if ("RAW_INT".equals(upperType)) {
            return toRawIntList(registers);
        }

        byte[] rawBytes = registersToBytes(registers);
        byte[] swapped = applyEndianness(rawBytes, endianness);

        return switch (upperType) {
            case "INT16" -> toInt16(swapped);
            case "UINT16" -> toUint16(swapped);
            case "INT32" -> toInt32(swapped);
            case "UINT32" -> toUint32(swapped);
            case "FLOAT32", "FLOAT" -> toFloat32(swapped);
            case "INT64", "LONG" -> toInt64(swapped);
            case "UINT64" -> toUint64(swapped);
            case "FLOAT64", "DOUBLE" -> toFloat64(swapped);
            case "STRING" -> toString(swapped);
            case "RAW_HEX" -> toHex(swapped);
            default -> toRawIntList(registers);
        };
    }

    /**
     * Helper to get register size of standard data types.
     */
    public static int getRegisterSize(String type) {
        if (type == null) return 0;
        String upperType = type.trim().toUpperCase();
        return switch (upperType) {
            case "INT16", "UINT16" -> 1;
            case "INT32", "UINT32", "FLOAT32", "FLOAT" -> 2;
            case "INT64", "LONG", "UINT64", "FLOAT64", "DOUBLE" -> 4;
            default -> 0; // STRING, RAW_HEX, RAW_INT take the whole array
        };
    }

    /**
     * Decodes a register array as a List of typed values based on the data type register size.
     * Always returns a List (even if quantity is 1).
     */
    public static List<Object> decodeValues(int[] registers, String type, String endianness) {
        List<Object> list = new ArrayList<>();
        if (registers == null || registers.length == 0) return list;

        String upperType = type.trim().toUpperCase();
        int regSize = getRegisterSize(upperType);

        if (regSize <= 0 || registers.length < regSize) {
            Object val = decodeValue(registers, type, endianness);
            if (val != null) {
                list.add(val);
            }
            return list;
        }

        for (int i = 0; i < registers.length; i += regSize) {
            if (i + regSize > registers.length) break;
            int[] subArray = new int[regSize];
            System.arraycopy(registers, i, subArray, 0, regSize);
            Object val = decodeValue(subArray, type, endianness);
            if (val != null) {
                list.add(val);
            }
        }
        return list;
    }

    /**
     * Encodes a value (which can be a single object or a List of objects) into a register array.
     */
    public static int[] encodeValues(Object value, String type, String endianness, int registerQty) {
        if (value == null) return new int[registerQty];

        String upperType = type.trim().toUpperCase();
        int regSize = getRegisterSize(upperType);

        if (value instanceof List<?> list) {
            if (regSize <= 0) {
                return encodeValue(value, type, endianness, registerQty);
            }
            int[] result = new int[list.size() * regSize];
            for (int i = 0; i < list.size(); i++) {
                int[] encodedItem = encodeValue(list.get(i), type, endianness, regSize);
                System.arraycopy(encodedItem, 0, result, i * regSize, regSize);
            }
            return result;
        } else {
            return encodeValue(value, type, endianness, registerQty);
        }
    }
}

