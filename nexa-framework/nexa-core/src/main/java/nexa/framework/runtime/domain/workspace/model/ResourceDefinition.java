package nexa.framework.runtime.domain.workspace.model;

import java.util.Map;

/**
 * ResourceDefinition merepresentasikan infrastruktur berat (database pools, mqtt clients)
 * yang didefinisikan secara terpisah dari data flow.
 */
public record ResourceDefinition(String id, String type, Map<String, Object> config) {
    public ResourceDefinition {
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
