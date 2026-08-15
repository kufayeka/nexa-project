package nexa.framework.runtime.domain.workspace.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Defines a message route between two nodes. */
public record ConnectionDefinition(
        @JsonProperty("id") String id,
        @JsonProperty("sourceNodeId") String sourceNodeId,
        @JsonProperty("sourcePort") String sourcePort,
        @JsonProperty("targetNodeId") String targetNodeId,
        @JsonProperty("enabled") Boolean enabled) {

    public ConnectionDefinition(String id, String sourceNodeId, String sourcePort, String targetNodeId) {
        this(id, sourceNodeId, sourcePort, targetNodeId, true);
    }

    public ConnectionDefinition {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (sourcePort == null || sourcePort.isBlank()) sourcePort = "default";
        if (enabled == null) enabled = true;
    }
}
