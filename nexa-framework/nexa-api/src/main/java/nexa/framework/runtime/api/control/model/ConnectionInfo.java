package nexa.framework.runtime.api.control.model;

import java.io.Serializable;

public record ConnectionInfo(
                String sourceNodeId,
                String targetNodeId,
                boolean enabled,
                long messageCount) implements Serializable {
}