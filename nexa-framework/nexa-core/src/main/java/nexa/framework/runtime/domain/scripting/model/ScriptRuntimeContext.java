package nexa.framework.runtime.domain.scripting.model;

import java.time.Instant;
import java.util.Map;

public record ScriptRuntimeContext(
        String workspaceId,
        String flowId,
        String nodeId,
        String executionId,
        Instant createdAt,
        Instant deadline,
        Map<String, Object> executionData) {
}


