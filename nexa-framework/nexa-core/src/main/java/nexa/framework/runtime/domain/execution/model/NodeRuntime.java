package nexa.framework.runtime.domain.execution.model;

import nexa.framework.runtime.domain.deployment.model.CompiledNode;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class NodeRuntime {

    private final AtomicReference<CompiledNode> compiledNode;

    public NodeRuntime(CompiledNode compiledNode) {
        this.compiledNode = new AtomicReference<>(
                Objects.requireNonNull(compiledNode, "compiledNode must not be null"));
    }

    public String nodeId() {
        return compiledNode().id();
    }

    public CompiledNode compiledNode() {
        return compiledNode.get();
    }

    public void setCompiledNode(CompiledNode updatedNode) {
        compiledNode.set(Objects.requireNonNull(updatedNode, "updatedNode must not be null"));
    }
}


