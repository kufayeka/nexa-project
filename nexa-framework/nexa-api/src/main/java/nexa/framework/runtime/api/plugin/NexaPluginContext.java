package nexa.framework.runtime.api.plugin;

import java.util.Map;

/**
 * Jembatan akses dari Core Nexa ke dalam Plugin.
 */
public interface NexaPluginContext {
    /**
     * Mencari shared client resource (seperti MqttClient atau HikariDataSource)
     * berdasarkan Resource ID yang didefinisikan dalam JSON.
     */
    Object getSharedResource(String resourceId);

    /**
     * Mengakses compiler untuk melakukan uji jalan (dry-run) validator snippet skrip.
     * Mengembalikan true jika valid, false jika error, dan mendokumentasikan error di errorContainer.
     */
    boolean validateScript(String language, String script, Map<String, Object> errorContainer);
}
