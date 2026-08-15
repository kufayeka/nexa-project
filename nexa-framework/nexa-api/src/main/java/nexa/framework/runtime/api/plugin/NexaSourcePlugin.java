package nexa.framework.runtime.api.plugin;

import nexa.framework.runtime.api.model.RuntimeMessage;
import java.util.function.Consumer;

/**
 * Spesialisasi komponen input source plugin untuk mengalirkan data ke Nexa Core.
 */
public interface NexaSourcePlugin extends NexaPlugin, NexaPluginLifecycle {
    /**
     * Memasang pemancar pesan (emitter) dari input source ke alur engine Nexa.
     */
    void setEmitter(Consumer<RuntimeMessage> emitter);
}
