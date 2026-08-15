package nexa.framework.runtime.domain.deployment.model;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CompiledConnection {
    private final String id;
    private final String sourceNodeId;
    private final String sourcePort;
    private final String targetNodeId;
    private final AtomicBoolean enabled;

    public CompiledConnection(
            String id,
            String sourceNodeId,
            String sourcePort,
            String targetNodeId,
            boolean enabled) {
        this.id = id;
        this.sourceNodeId = sourceNodeId;
        this.sourcePort = sourcePort;
        this.targetNodeId = targetNodeId;
        this.enabled = new AtomicBoolean(enabled);
    }

    public String id() { return id; }
    public String sourceNodeId() { return sourceNodeId; }
    public String sourcePort() { return sourcePort; }
    public String targetNodeId() { return targetNodeId; }
    public boolean enabled() { return enabled.get(); }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}
