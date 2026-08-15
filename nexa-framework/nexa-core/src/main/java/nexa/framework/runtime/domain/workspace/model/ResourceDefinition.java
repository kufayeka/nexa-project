package nexa.framework.runtime.domain.workspace.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * ResourceDefinition merepresentasikan infrastruktur berat (database pools, mqtt clients)
 * yang didefinisikan secara terpisah dari data flow.
 *
 * enabled=false membuat resource tidak diinisialisasi/diaktifkan saat deployment.
 */
public record ResourceDefinition(
        String id,
        String type,
        boolean enabled,
        Map<String, Object> config) {

    /**
     * Backward-compatible constructor: resources tanpa enabled dianggap aktif.
     */
    public ResourceDefinition(String id, String type, Map<String, Object> config) {
        this(id, type, true, config);
    }

    @JsonCreator
    public static ResourceDefinition create(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type,
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("config") Map<String, Object> config) {
        return new ResourceDefinition(
                id,
                type,
                enabled == null || enabled,
                config);
    }

    public ResourceDefinition {
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
