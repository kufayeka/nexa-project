package nexa.framework.runtime.api.control.events;

import nexa.framework.runtime.api.model.RuntimeMessage;
import java.io.Serializable;

public record ScriptNodeFailureEvent(
        String workspaceId,
        String flowId,
        String nodeId,
        int lineNumber,
        String errorMessage,
        RuntimeMessage messagePayload,
        long timestamp) implements Serializable {
}