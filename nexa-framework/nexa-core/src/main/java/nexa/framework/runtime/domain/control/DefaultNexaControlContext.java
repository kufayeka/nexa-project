package nexa.framework.runtime.domain.control;

import nexa.framework.runtime.api.control.*;
import nexa.framework.runtime.api.control.events.NexaEventBus;

public class DefaultNexaControlContext implements NexaControlContext {
    private final WorkspaceControl workspaceControl;
    private final NodeControl nodeControl;
    private final ConnectionControl connectionControl;
    private final RuntimeControl runtimeControl;
    private final NexaEventBus eventBus;

    public DefaultNexaControlContext(
            WorkspaceControl workspaceControl,
            NodeControl nodeControl,
            ConnectionControl connectionControl,
            RuntimeControl runtimeControl,
            NexaEventBus eventBus) {
        this.workspaceControl = workspaceControl;
        this.nodeControl = nodeControl;
        this.connectionControl = connectionControl;
        this.runtimeControl = runtimeControl;
        this.eventBus = eventBus;
    }

    @Override
    public WorkspaceControl getWorkspaceControl() {
        return workspaceControl;
    }

    @Override
    public NodeControl getNodeControl() {
        return nodeControl;
    }

    @Override
    public ConnectionControl getConnectionControl() {
        return connectionControl;
    }

    @Override
    public RuntimeControl getRuntimeControl() {
        return runtimeControl;
    }

    @Override
    public NexaEventBus getEventBus() {
        return eventBus;
    }
}
