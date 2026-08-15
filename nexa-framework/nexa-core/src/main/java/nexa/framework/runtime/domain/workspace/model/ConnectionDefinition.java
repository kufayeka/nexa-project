package nexa.framework.runtime.domain.workspace.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ConnectionDefinition mendefinisikan hubungan antar node (rute pesan).
 * Menghubungkan sourcePort dari sourceNode ke targetNode.
 */
public record ConnectionDefinition(
        @JsonProperty("sourceNodeId") String sourceNodeId,
        @JsonProperty("sourcePort") String sourcePort,
        @JsonProperty("targetNodeId") String targetNodeId
) {

    public ConnectionDefinition {
        // Jika port asal kosong, default-kan ke port "default"
        if (sourcePort == null || sourcePort.isBlank()) {
            sourcePort = "default";
        }
    }
}

