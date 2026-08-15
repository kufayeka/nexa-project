package nexa.framework.runtime.domain.deployment.model;

public record CompiledConnection(
        String id,
        String sourceNodeId,
        String sourcePort,
        String targetNodeId,
        boolean enabled) {

    public CompiledConnection(
            String id,
            String sourceNodeId,
            String sourcePort,
            String targetNodeId) {
        this(id, sourceNodeId, sourcePort, targetNodeId, true);
    }
}
