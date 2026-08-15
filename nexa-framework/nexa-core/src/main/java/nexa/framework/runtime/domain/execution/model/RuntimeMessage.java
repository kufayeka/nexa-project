package nexa.framework.runtime.domain.execution.model;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Kelas kompatibilitas ke belakang (Backwards Compatibility) untuk RuntimeMessage.
 * Menginduk ke kelas baru di nexa-api agar kode eksternal/legacy tetap berjalan lancar.
 * 
 * @deprecated Gunakan {@link nexa.framework.runtime.api.model.RuntimeMessage} sebagai gantinya.
 */
@Deprecated(since = "2.0", forRemoval = false)
public final class RuntimeMessage extends nexa.framework.runtime.api.model.RuntimeMessage {

    public RuntimeMessage() {
        super();
    }

    public RuntimeMessage(Map<String, Object> values) {
        super(values);
    }

    private RuntimeMessage(ConcurrentMap<String, Object> values, boolean copyValues) {
        super(values, copyValues);
    }

    public static RuntimeMessage shared(ConcurrentMap<String, Object> values) {
        return new RuntimeMessage(values, false);
    }
}
