package nexa.framework.runtime.api.control;

import nexa.framework.runtime.api.control.events.NexaEventBus;

public interface NexaControlContext {
    WorkspaceControl getWorkspaceControl();

    NodeControl getNodeControl();

    ConnectionControl getConnectionControl();

    RuntimeControl getRuntimeControl();

    NexaEventBus getEventBus();
}