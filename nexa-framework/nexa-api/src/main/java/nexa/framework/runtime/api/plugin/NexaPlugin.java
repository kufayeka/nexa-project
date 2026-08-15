package nexa.framework.runtime.api.plugin;

/**
 * Kontrak dasar untuk seluruh plugin Nexa.
 */
public interface NexaPlugin {
    /**
     * Mengembalikan jenis/tipe plugin (misal: "postgres-pool", "mqtt-in").
     */
    String getPluginType();
}
