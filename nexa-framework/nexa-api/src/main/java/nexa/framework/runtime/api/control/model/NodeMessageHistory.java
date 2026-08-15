package nexa.framework.runtime.api.control.model;

import nexa.framework.runtime.api.model.RuntimeMessage;
import java.io.Serializable;
import java.util.List;

public record NodeMessageHistory(
        String nodeId,
        List<RuntimeMessage> incoming,
        List<RuntimeMessage> outgoing) implements Serializable {
}