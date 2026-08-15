package nexa.framework.runtime.domain.workspace.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * FlowDefinition merepresentasikan struktur graf/alur data yang terdiri dari
 * kumpulan node dan koneksi antar node tersebut.
 */
public record FlowDefinition(
        String id,
        String name,
        boolean enabled,
        List<NodeDefinition> nodes,
        List<ConnectionDefinition> connections
) {

    /**
     * Konstruktor kanonik manual untuk menormalisasi nilai null dan
     * memastikan list nodes & connections bersifat immutable.
     */
    public FlowDefinition(
            String id,
            String name,
            boolean enabled,
            List<NodeDefinition> nodes,
            List<ConnectionDefinition> connections
    ) {
        this.id = id;
        this.name = name == null || name.isBlank() ? id : name;
        this.enabled = enabled;
        this.nodes = nodes == null ? List.of() : List.copyOf(new ArrayList<>(nodes));
        this.connections = connections == null ? List.of() : List.copyOf(new ArrayList<>(connections));
    }

    @JsonCreator
    public static FlowDefinition create(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("nodes") List<NodeDefinition> nodes,
            @JsonProperty("connections") List<ConnectionDefinition> connections) {
        return new FlowDefinition(
                id,
                name,
                enabled == null || enabled,
                nodes,
                connections
        );
    }
}

