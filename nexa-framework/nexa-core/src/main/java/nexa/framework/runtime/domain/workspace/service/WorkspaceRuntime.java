package nexa.framework.runtime.domain.workspace.service;

import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.api.state.TypedTagStore;
import nexa.framework.tags.TagDefinition;
import nexa.framework.tags.TagRuntime;
import nexa.framework.runtime.domain.deployment.model.CompiledFlow;
import nexa.framework.runtime.domain.deployment.model.CompiledWorkspace;
import nexa.framework.runtime.domain.deployment.service.FlowCompiler;
import nexa.framework.runtime.domain.execution.CompiledFlowRuntime;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Workspace boundary for compiled flows and the standalone high-speed tag runtime. */
public final class WorkspaceRuntime implements AutoCloseable {
    private final FlowCompiler compiler;
    private final Map<String, CompiledFlowRuntime> flowRuntimes = new LinkedHashMap<>();
    private volatile WorkspaceDefinition definition;
    private volatile CompiledWorkspace compiledWorkspace;
    private volatile TypedTagStore legacyTagStore;
    private volatile TagRuntime tagRuntime;
    private volatile Map<String, Integer> tagSlots = Map.of();

    public WorkspaceRuntime(FlowCompiler compiler) {
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
    }

    public synchronized void deploy(WorkspaceDefinition workspace) {
        Objects.requireNonNull(workspace, "workspace must not be null");
        CompiledWorkspace compiled = compiler.compileWorkspace(workspace);
        Map<String, Integer> slots = compiler.tagSlotsSnapshot();
        TagRuntime previous = tagRuntime;
        TagRuntime next = null;
        TypedTagStore legacy = null;
        Map<String, CompiledFlowRuntime> runtimes = new LinkedHashMap<>();

        if (!workspace.tags().isEmpty()) {
            Map<String, TagDefinition> definitions = new LinkedHashMap<>();
            for (TagDefinition tag : workspace.tags()) definitions.put(tag.name(), tag);
            List<TagDefinition> ordered = new ArrayList<>();
            slots.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .map(definitions::get)
                    .filter(Objects::nonNull)
                    .forEach(ordered::add);
            workspace.tags().stream()
                    .filter(tag -> !slots.containsKey(tag.name()))
                    .sorted(Comparator.comparing(TagDefinition::name))
                    .forEach(ordered::add);
            next = new TagRuntime(ordered);
            for (Map.Entry<String, CompiledFlow> entry : compiled.flowsById().entrySet()) {
                runtimes.put(entry.getKey(), new CompiledFlowRuntime(entry.getValue(), next));
            }
        } else {
            int size = slots.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
            legacy = new TypedTagStore(size, size, size, size);
            for (Map.Entry<String, CompiledFlow> entry : compiled.flowsById().entrySet()) {
                runtimes.put(entry.getKey(), new CompiledFlowRuntime(entry.getValue(), legacy));
            }
        }

        definition = workspace;
        compiledWorkspace = compiled;
        tagSlots = Map.copyOf(new LinkedHashMap<>(slots));
        tagRuntime = next;
        legacyTagStore = legacy;
        flowRuntimes.clear();
        flowRuntimes.putAll(runtimes);
        if (previous != null) previous.close();
    }

    public WorkspaceDefinition definition() { return definition; }
    public CompiledWorkspace compiledWorkspace() { return compiledWorkspace; }
    /** Compatibility accessor for workspaces that have not migrated to explicit tags. */
    public TypedTagStore tagStore() { return legacyTagStore; }
    public TagRuntime tagRuntime() { return tagRuntime; }
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

    public Object readTag(String path) {
        if (tagRuntime == null) throw new IllegalStateException("Workspace has no explicit tag configuration");
        return tagRuntime.read(path);
    }

    public void writeTag(String path, Object value) {
        if (tagRuntime == null) throw new IllegalStateException("Workspace has no explicit tag configuration");
        tagRuntime.write(path, value);
    }

    public int readTagInt(String name) {
        return tagRuntime != null ? tagRuntime.readInt(tagSlot(name)) : legacyTagStore.readInt(tagSlot(name));
    }
    public void writeTagInt(String name, int value) {
        if (tagRuntime != null) tagRuntime.writeInt(tagSlot(name), value); else legacyTagStore.writeInt(tagSlot(name), value);
    }
    public long readTagLong(String name) {
        return tagRuntime != null ? tagRuntime.readLong(tagSlot(name)) : legacyTagStore.readLong(tagSlot(name));
    }
    public void writeTagLong(String name, long value) {
        if (tagRuntime != null) tagRuntime.writeLong(tagSlot(name), value); else legacyTagStore.writeLong(tagSlot(name), value);
    }
    public double readTagDouble(String name) {
        return tagRuntime != null ? tagRuntime.readDouble(tagSlot(name)) : legacyTagStore.readDouble(tagSlot(name));
    }
    public void writeTagDouble(String name, double value) {
        if (tagRuntime != null) tagRuntime.writeDouble(tagSlot(name), value); else legacyTagStore.writeDouble(tagSlot(name), value);
    }
    public Object readTagObject(String name) {
        return tagRuntime != null ? tagRuntime.readSlot(tagSlot(name)) : legacyTagStore.readObject(tagSlot(name));
    }
    public void writeTagObject(String name, Object value) {
        if (tagRuntime != null) tagRuntime.writeObject(tagSlot(name), value); else legacyTagStore.writeObject(tagSlot(name), value);
    }

    private CompiledFlowRuntime runtime(String flowId) {
        CompiledFlowRuntime runtime = flowRuntimes.get(flowId);
        if (runtime == null) throw new IllegalArgumentException("Unknown flow id " + flowId);
        return runtime;
    }

    @Override
    public void close() {
        TagRuntime runtime = tagRuntime;
        if (runtime != null) runtime.close();
    }
}
