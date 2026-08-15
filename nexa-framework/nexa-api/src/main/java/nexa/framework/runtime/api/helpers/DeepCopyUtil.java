package nexa.framework.runtime.api.helpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeepCopyUtil {

    private DeepCopyUtil() {
    }

    public static Map<String, Object> deepCopyMap(Map<String, Object> input) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    public static Object deepCopyValue(Object value) {
        return switch (value) {
            case null -> null;
            case String s -> s;
            case Number n -> n;
            case Boolean b -> b;
            case Character c -> c;
            case Map<?, ?> map -> {
                Map<String, Object> copied = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copied.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
                }
                yield copied;
            }
            case List<?> list -> {
                List<Object> copied = new ArrayList<>(list.size());
                for (Object item : list) {
                    copied.add(deepCopyValue(item));
                }
                yield copied;
            }
            case Object[] array -> {
                List<Object> copied = new ArrayList<>(array.length);
                for (Object item : array) {
                    copied.add(deepCopyValue(item));
                }
                yield copied;
            }
            default -> throw new IllegalArgumentException("Unsupported message value type: " + value.getClass().getName());
        };
    }
}
