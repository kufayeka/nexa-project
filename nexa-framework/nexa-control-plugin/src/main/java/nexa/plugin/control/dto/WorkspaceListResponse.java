package nexa.plugin.control.dto;

import nexa.framework.runtime.api.control.model.WorkspaceMetaInfo;

import java.io.Serializable;
import java.util.List;

/** Typed workspace collection response. */
public record WorkspaceListResponse(
        List<WorkspaceMetaInfo> workspaces) implements Serializable {
}
