package nexa.framework.runtime.api.control.model;

import java.io.Serializable;

public record NodeInfo(
        String nodeId,
        String flowId,
        String nodeType,
        boolean enabled,
        boolean hasBreakpoint,
        boolean isPaused,
        long processedMessageCount,
        long errorMessageCount) implements Serializable {
}