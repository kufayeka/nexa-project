package nexa.framework.runtime.api.plugin;

import nexa.framework.runtime.api.model.RuntimeMessage;

/**
 * Spesialisasi komponen pemrosesan tengah (Executor / Function).
 */
public interface NexaFunctionPlugin extends NexaPlugin, NexaPluginLifecycle {
    /**
     * Melakukan transformasi pesan dan mengembalikan pesan hasil transformasi.
     * Jika mengembalikan null, pesan tidak akan diteruskan ke downstream.
     */
    RuntimeMessage process(RuntimeMessage msg);
}
