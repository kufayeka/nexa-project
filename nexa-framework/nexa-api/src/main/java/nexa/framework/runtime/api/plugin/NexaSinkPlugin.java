package nexa.framework.runtime.api.plugin;

import nexa.framework.runtime.api.model.RuntimeMessage;

/**
 * Spesialisasi komponen keluaran akhir (Sink / Output).
 */
public interface NexaSinkPlugin extends NexaPlugin, NexaPluginLifecycle {
    /**
     * Menerima pesan akhir dari pipeline aliran data untuk dibuang/dikirim.
     */
    void consume(RuntimeMessage msg);
}
