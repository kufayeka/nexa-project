package nexa.framework.runtime.domain.workspace.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * ConnectionDefinition mendefinisikan hubungan antar node (rute pesan).
 * Setiap connection memiliki ID unik yang didefinisikan oleh workspace.
 */
public record ConnectionDefinition(
        @JsonProperty("id") String id,
        @JsonProperty("sourceNodeId") String sourceNodeId,
        @JsonProperty("sourcePort") String sourcePort,
        @JsonProperty("targetNodeId") String targetNodeId,
        @JsonProperty("enabled") boolean enabled) {

    public ConnectionDefinition {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }

        if (sourcePort == null || sourcePort.isBlank()) {
            sourcePort = "default";
        }
    }

    @JsonCreator
    public static ConnectionDefinition create(
            @JsonProperty("id") String id,
            @JsonProperty("sourceNodeId") String sourceNodeId,
            @JsonProperty("sourcePort") String sourcePort,
            @JsonProperty("targetNodeId") String targetNodeId,
            @JsonProperty("enabled") Boolean enabled) {
        return new ConnectionDefinition(
                id,
                sourceNodeId,
                sourcePort,
                targetNodeId,
                enabled == null || enabled);
    }
}
