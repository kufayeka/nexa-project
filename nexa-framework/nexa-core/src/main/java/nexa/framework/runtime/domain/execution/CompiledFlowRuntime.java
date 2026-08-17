package nexa.framework.runtime.domain.execution;

import nexa.framework.runtime.api.NexaCompiledNode;
import nexa.framework.runtime.api.NexaExecutionContext;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.deployment.model.CompiledFlow;
import nexa.framework.runtime.domain.deployment.model.CompiledNode;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Minimal synchronous flow execution runtime.
 *
 * This is the first executable vertical slice of Nexa: an input trigger enters
 * a compiled flow, executor nodes invoke NexaCompiledNode, and send() routes
 * RuntimeMessage instances through the compiled connection graph to outputs.
 */
public final class CompiledFlowRuntime {

    private final CompiledFlow flow;
    private final Map<String, Consumer<RuntimeMessage>> outputHandlers = new ConcurrentHashMap<>();

    public CompiledFlowRuntime(CompiledFlow flow) {
        this.flow = Objects.requireNonNull(flow, "flow must not be null");
    }

    public CompiledFlow flow() {
        return flow;
    }

    public void registerOutput(String nodeId, Consumer<RuntimeMessage> handler) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        outputHandlers.put(nodeId, handler);
    }

    /** Triggers an input node with a new message. */
    public void trigger(String inputNodeId, RuntimeMessage message) {
        requireMessage(message);
        CompiledNode input = requireNode(inputNodeId);
        if (input.category() != NodeCategory.INPUT) {
            throw new IllegalArgumentException("Node " + inputNodeId + " is not an INPUT node");
        }
        if (!input.enabled()) {
            return;
        }
        dispatch(input.id(), "default", message);
    }

    private void dispatch(String sourceNodeId, String sourcePort, RuntimeMessage message) {
        List<String> targets = flow.targets(sourceNodeId, sourcePort);
        for (String targetNodeId : targets) {
            if (!flow.connectionEnabled(sourceNodeId, sourcePort, targetNodeId)) {
                continue;
            }

            CompiledNode target = requireNode(targetNodeId);
            if (!target.enabled()) {
                continue;
            }

            RuntimeMessage branchMessage = targets.size() > 1 ? message.deepCopy() : message;
            execute(target, branchMessage);
        }
    }

    private void execute(CompiledNode node, RuntimeMessage message) {
        switch (node.category()) {
            case INPUT -> dispatch(node.id(), "default", message);
            case EXECUTOR -> executeCompiled(node, message);
            case OUTPUT -> {
                Consumer<RuntimeMessage> handler = outputHandlers.get(node.id());
                if (handler == null) {
                    throw new IllegalStateException("No output handler registered for node " + node.id());
                }
                handler.accept(message);
            }
        }
    }

    private void executeCompiled(CompiledNode node, RuntimeMessage message) {
        NexaCompiledNode executable = node.executableNode();
        if (executable == null) {
            throw new IllegalStateException("Executor node " + node.id() + " has no compiled executable");
        }

        executable.execute(message, new RuntimeExecutionContext(node.id()));
    }

    private final class RuntimeExecutionContext implements NexaExecutionContext {
        private final String sourceNodeId;

        private RuntimeExecutionContext(String sourceNodeId) {
            this.sourceNodeId = sourceNodeId;
        }

        @Override
        public void send(RuntimeMessage msg) {
            send("default", msg);
        }

        @Override
        public void send(String port, RuntimeMessage msg) {
            requireMessage(msg);
            dispatch(sourceNodeId, port, msg);
        }

        @Override
        public void send(List<String> ports, RuntimeMessage msg) {
            requireMessage(msg);
            for (String port : new ArrayList<>(ports)) {
                send(port, msg);
            }
        }

        @Override
        public Object callHostCapability(String namespace, String name, List<Object> args) {
            throw new UnsupportedOperationException(
                    "Host capability is not available in the minimal runtime: " + namespace + "." + name);
        }

        @Override public int readTagInt(int slot) { throw unsupportedTags(); }
        @Override public long readTagLong(int slot) { throw unsupportedTags(); }
        @Override public double readTagDouble(int slot) { throw unsupportedTags(); }
        @Override public Object readTagObject(int slot) { throw unsupportedTags(); }
        @Override public void writeTagInt(int slot, int value) { throw unsupportedTags(); }
        @Override public void writeTagLong(int slot, long value) { throw unsupportedTags(); }
        @Override public void writeTagDouble(int slot, double value) { throw unsupportedTags(); }
        @Override public void writeTagObject(int slot, Object value) { throw unsupportedTags(); }

        private UnsupportedOperationException unsupportedTags() {
            return new UnsupportedOperationException("TagStore is not wired into the minimal runtime yet");
        }
    }

    private CompiledNode requireNode(String nodeId) {
        CompiledNode node = flow.node(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node id " + nodeId);
        }
        return node;
    }

    private void requireMessage(RuntimeMessage message) {
        Objects.requireNonNull(message, "message must not be null");
    }
}
