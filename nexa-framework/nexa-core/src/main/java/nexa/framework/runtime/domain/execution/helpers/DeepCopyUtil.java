package nexa.framework.runtime.domain.execution.helpers;

import java.util.Map;

/**
 * Kelas kompatibilitas ke belakang (Backwards Compatibility) untuk DeepCopyUtil.
 * Mendelegasikan tugas ke kelas baru di nexa-api agar kode eksternal/legacy tetap berjalan lancar.
 * 
 * @deprecated Gunakan {@link nexa.framework.runtime.api.helpers.DeepCopyUtil} sebagai gantinya.
 */
@Deprecated(since = "2.0", forRemoval = false)
public final class DeepCopyUtil {

    private DeepCopyUtil() {
    }

    public static Map<String, Object> deepCopyMap(Map<String, Object> input) {
        return nexa.framework.runtime.api.helpers.DeepCopyUtil.deepCopyMap(input);
    }

    public static Object deepCopyValue(Object value) {
        return nexa.framework.runtime.api.helpers.DeepCopyUtil.deepCopyValue(value);
    }
}
