package nexa.framework.runtime.domain.scripting.api;

import nexa.framework.runtime.domain.scripting.model.ScriptRuntimeContext;

import java.time.Instant;
import java.util.Map;

public record ScriptRuntimeApi(
        String workspaceId,
        String flowId,
        String nodeId,
        String executionId,
        Instant createdAt,
        Instant deadline,
        Map<String, Object> executionData) {

    public static ScriptRuntimeApi fromContext(ScriptRuntimeContext context) {
        return new ScriptRuntimeApi(
                context.workspaceId(),
                context.flowId(),
                context.nodeId(),
                context.executionId(),
                context.createdAt(),
                context.deadline(),
                context.executionData());
    }
}


