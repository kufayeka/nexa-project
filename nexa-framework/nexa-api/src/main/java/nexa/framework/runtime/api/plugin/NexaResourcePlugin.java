package nexa.framework.runtime.api.plugin;

/**
 * Spesialisasi plugin untuk resource eksternal terbagi (shared resource pool).
 */
public interface NexaResourcePlugin extends NexaPlugin, NexaPluginLifecycle {
    /**
     * Mengembalikan native client pool (seperti HikariDataSource, shared MqttClient).
     */
    Object getNativeClient();
}
