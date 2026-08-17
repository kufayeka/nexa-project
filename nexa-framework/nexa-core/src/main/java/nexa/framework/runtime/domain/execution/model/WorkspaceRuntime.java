package nexa.framework.runtime.domain.execution.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WorkspaceRuntime {

    private final String workspaceId;
    private final ConcurrentMap<String, FlowRuntime> flowsById;
    private final AtomicBoolean enabled;
    private final nexa.framework.runtime.api.state.TypedTagStore tagStore;

    public WorkspaceRuntime(String workspaceId, boolean enabled) {
        this.workspaceId = workspaceId;
        this.flowsById = new ConcurrentHashMap<>();
        this.enabled = new AtomicBoolean(enabled);
        this.tagStore = new nexa.framework.runtime.api.state.TypedTagStore(1024, 1024, 1024, 1024); // 1024 slots for lock-free array
    }

    public nexa.framework.runtime.api.state.TypedTagStore tagStore() {
        return tagStore;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public ConcurrentMap<String, FlowRuntime> flowsById() {
        return flowsById;
    }

    public boolean enabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}


