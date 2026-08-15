package nexa.framework.runtime.domain.scripting.registry;

import nexa.framework.runtime.api.plugin.NexaPlugin;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PluginRegistry memetakan metadata tipe plugin eksternal (Plugin Class)
 * dan menampung instance plugin aktif per node ID.
 */
public final class PluginRegistry {
    private static final ConcurrentHashMap<String, Class<? extends NexaPlugin>> meta = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, NexaPlugin> instances = new ConcurrentHashMap<>();

    private PluginRegistry() {
    }

    public static void registerMeta(String type, Class<? extends NexaPlugin> clazz) {
        meta.put(type, clazz);
    }

    public static Class<? extends NexaPlugin> getMeta(String type) {
        return meta.get(type);
    }

    public static boolean hasPlugin(String type) {
        return type != null && meta.containsKey(type);
    }

    public static void registerInstance(String nodeId, NexaPlugin instance) {
        instances.put(nodeId, instance);
    }

    public static NexaPlugin getInstance(String nodeId) {
        return instances.get(nodeId);
    }

    public static void removeInstance(String nodeId) {
        NexaPlugin instance = instances.remove(nodeId);
        if (instance instanceof nexa.framework.runtime.api.plugin.NexaPluginLifecycle lifecycle) {
            try {
                lifecycle.onStop();
            } catch (Exception ignored) {
            }
        }
    }

    public static void clearAll() {
        instances.values().forEach(instance -> {
            if (instance instanceof nexa.framework.runtime.api.plugin.NexaPluginLifecycle lifecycle) {
                try {
                    lifecycle.onStop();
                } catch (Exception ignored) {
                }
            }
        });
        instances.clear();
    }
}
