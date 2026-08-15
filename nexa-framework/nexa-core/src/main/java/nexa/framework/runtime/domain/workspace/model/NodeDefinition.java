package nexa.framework.runtime.domain.workspace.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NodeDefinition merepresentasikan konfigurasi dasar dan properti dari sebuah Node
 * yang dimuat dari JSON.
 */
public record NodeDefinition(
        String id,
        NodeCategory category,
        String type,
        String language,
        boolean enabled,
        InputExecutionPolicyDefinition inputPolicy,
        Map<String, Object> config
) {

    /**
     * Konstruktor kanonik manual untuk membersihkan dan menormalisasi input null
     * agar runtime terhindar dari NullPointerException akibat konfigurasi kosong.
     */
    public NodeDefinition(
            String id,
            NodeCategory category,
            String type,
            String language,
            boolean enabled,
            InputExecutionPolicyDefinition inputPolicy,
            Map<String, Object> config
    ) {
        this.id = id;
        this.category = category;
        this.type = type;
        this.language = language;
        this.enabled = enabled;
        this.inputPolicy = inputPolicy == null ? new InputExecutionPolicyDefinition(null) : inputPolicy;
        this.config = config == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(config));
    }

    @JsonCreator
    public static NodeDefinition create(
            @JsonProperty("id") String id,
            @JsonProperty("category") NodeCategory category,
            @JsonProperty("type") String type,
            @JsonProperty("language") String language,
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("inputPolicy") InputExecutionPolicyDefinition inputPolicy,
            @JsonProperty("config") Map<String, Object> config) {
        return new NodeDefinition(
                id,
                category,
                type,
                language,
                enabled == null || enabled,
                inputPolicy,
                config
        );
    }
}

