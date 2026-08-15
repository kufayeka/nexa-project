package nexa.framework.runtime.api.plugin;

import java.util.Map;

/**
 * Mendefinisikan fase-fase daur hidup (lifecycle) plugin Nexa.
 */
public interface NexaPluginLifecycle {

    /**
     * Fase 1: Inisialisasi konfigurasi dari JSON node atau resource.
     */
    void onInit(String targetId, Map<String, Object> config, NexaPluginContext context) throws Exception;

    /**
     * Fase 2: Aktivasi fungsionalitas (membuka port, koneksi TCP, dll.).
     */
    void onStart() throws Exception;

    /**
     * Fase 3: Deaktivasi fungsionalitas (cleanup resource, close sockets/connections).
     */
    void onStop();
}
