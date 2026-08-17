package nexa.framework.runtime.domain.workspace.service;

import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.api.state.TypedTagStore;
import nexa.framework.runtime.domain.deployment.model.CompiledFlow;
import nexa.framework.runtime.domain.deployment.model.CompiledWorkspace;
import nexa.framework.runtime.domain.deployment.service.FlowCompiler;
import nexa.framework.runtime.domain.execution.CompiledFlowRuntime;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Executable workspace boundary for the first Level-5 vertical slice.
 * A workspace owns one shared tag store and one compiled runtime per flow.
 */
public final class WorkspaceRuntime {
    private final FlowCompiler compiler;
    private final Map<String, CompiledFlowRuntime> flowRuntimes = new LinkedHashMap<>();
    private volatile WorkspaceDefinition definition;
    private volatile CompiledWorkspace compiledWorkspace;
    private volatile TypedTagStore tagStore;
    private volatile Map<String, Integer> tagSlots = Map.of();

    public WorkspaceRuntime(FlowCompiler compiler) {
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
    }

    /** Compiles and atomically installs the workspace as the current runtime. */
    public synchronized void deploy(WorkspaceDefinition workspace) {
        Objects.requireNonNull(workspace, "workspace must not be null");
        CompiledWorkspace compiled = compiler.compileWorkspace(workspace);
        Map<String, Integer> slots = compiler.tagSlotsSnapshot();
        int size = slots.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        TypedTagStore store = new TypedTagStore(size, size, size, size);
        Map<String, CompiledFlowRuntime> runtimes = new LinkedHashMap<>();
        for (Map.Entry<String, CompiledFlow> entry : compiled.flowsById().entrySet()) {
            runtimes.put(entry.getKey(), new CompiledFlowRuntime(entry.getValue(), store));
        }
        this.definition = workspace;
        this.compiledWorkspace = compiled;
        this.tagSlots = Map.copyOf(new LinkedHashMap<>(slots));
        this.tagStore = store;
        flowRuntimes.clear();
        flowRuntimes.putAll(runtimes);
    }

    public WorkspaceDefinition definition() { return definition; }
    public CompiledWorkspace compiledWorkspace() { return compiledWorkspace; }
    public TypedTagStore tagStore() { return tagStore; }
    public Map<String, Integer> tagSlots() { return tagSlots; }

    public void registerOutput(String flowId, String nodeId, Consumer<RuntimeMessage> handler) {
        runtime(flowId).registerOutput(nodeId, handler);
    }

    public void trigger(String flowId, String inputNodeId, RuntimeMessage message) {
        if (compiledWorkspace == null || !compiledWorkspace.enabled()) return;
        CompiledFlow flow = compiledWorkspace.flowsById().get(flowId);
        if (flow == null) throw new IllegalArgumentException("Unknown flow id " + flowId);
        if (!flow.enabled()) return;
        runtime(flowId).trigger(inputNodeId, message);
    }

    public int tagSlot(String name) {
        Integer slot = tagSlots.get(name);
        if (slot == null) throw new IllegalArgumentException("Unknown tag: " + name);
        return slot;
    }

    public int readTagInt(String name) { return tagStore.readInt(tagSlot(name)); }
    public void writeTagInt(String name, int value) { tagStore.writeInt(tagSlot(name), value); }
    public long readTagLong(String name) { return tagStore.readLong(tagSlot(name)); }
    public void writeTagLong(String name, long value) { tagStore.writeLong(tagSlot(name), value); }
    public double readTagDouble(String name) { return tagStore.readDouble(tagSlot(name)); }
    public void writeTagDouble(String name, double value) { tagStore.writeDouble(tagSlot(name), value); }
    public Object readTagObject(String name) { return tagStore.readObject(tagSlot(name)); }
    public void writeTagObject(String name, Object value) { tagStore.writeObject(tagSlot(name), value); }

    private CompiledFlowRuntime runtime(String flowId) {
        CompiledFlowRuntime runtime = flowRuntimes.get(flowId);
        if (runtime == null) throw new IllegalArgumentException("Unknown flow id " + flowId);
        return runtime;
    }
}
