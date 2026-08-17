package nexa.framework.runtime.domain.workspace.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import nexa.framework.tags.TagDefinition;

import java.util.ArrayList;
import java.util.List;

/** Workspace configuration: resources, tags and executable flows. */
public record WorkspaceDefinition(
        String id,
        boolean enabled,
        List<ResourceDefinition> resources,
        List<TagDefinition> tags,
        List<FlowDefinition> flows
) {
    public WorkspaceDefinition(
            String id,
            boolean enabled,
            List<ResourceDefinition> resources,
            List<TagDefinition> tags,
            List<FlowDefinition> flows) {
        this.id = id;
        this.enabled = enabled;
        this.resources = resources == null ? List.of() : List.copyOf(new ArrayList<>(resources));
        this.tags = tags == null ? List.of() : List.copyOf(new ArrayList<>(tags));
        this.flows = flows == null ? List.of() : List.copyOf(new ArrayList<>(flows));
    }

    /** Compatibility constructor for existing workspaces without explicit tags. */
    public WorkspaceDefinition(String id, boolean enabled, List<ResourceDefinition> resources, List<FlowDefinition> flows) {
        this(id, enabled, resources, List.of(), flows);
    }

    /** Compatibility constructor for flow-only definitions. */
    public WorkspaceDefinition(String id, boolean enabled, List<FlowDefinition> flows) {
        this(id, enabled, List.of(), List.of(), flows);
    }

    @JsonCreator
    public static WorkspaceDefinition create(
            @JsonProperty("id") String id,
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("resources") List<ResourceDefinition> resources,
            @JsonProperty("tags") List<TagDefinition> tags,
            @JsonProperty("flows") List<FlowDefinition> flows) {
        return new WorkspaceDefinition(id, enabled == null || enabled, resources, tags, flows);
    }
}
