package nexa.framework.runtime.api.control.model;

import java.io.Serializable;

public record WorkspaceMetaInfo(
        String workspaceId,
        String name,
        String description,
        boolean enabled,
        int flowCount,
        int nodeCount) implements Serializable {
}