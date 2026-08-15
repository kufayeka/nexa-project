package nexa.framework.runtime.domain.execution.service;

import nexa.framework.runtime.api.plugin.NexaResourcePlugin;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GlobalResourceRegistry mengelola daur hidup runtime shared resource plugins
 * secara thread-safe menggunakan ConcurrentHashMap.
 */
public final class GlobalResourceRegistry {
    private final ConcurrentHashMap<String, NexaResourcePlugin> activeResources = new ConcurrentHashMap<>();

    public void registerResource(String id, NexaResourcePlugin resource) {
        activeResources.put(id, resource);
    }

    public NexaResourcePlugin getResource(String id) {
        return activeResources.get(id);
    }

    public void removeResource(String id) {
        NexaResourcePlugin resource = activeResources.remove(id);
        if (resource != null) {
            try {
                resource.onStop();
            } catch (Exception ignored) {
            }
        }
    }

    public void clearAll() {
        activeResources.values().forEach(resource -> {
            try {
                resource.onStop();
            } catch (Exception ignored) {
            }
        });
        activeResources.clear();
    }
}
