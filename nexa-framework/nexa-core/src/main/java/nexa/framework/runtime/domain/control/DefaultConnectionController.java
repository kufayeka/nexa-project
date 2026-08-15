package nexa.framework.runtime.domain.control;

import nexa.framework.runtime.api.control.ConnectionControl;
import nexa.framework.runtime.api.control.model.ConnectionInfo;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.execution.service.DefaultRuntimeEngine;
import nexa.framework.runtime.domain.execution.model.WorkspaceRuntime;
import nexa.framework.runtime.domain.execution.model.FlowRuntime;
import nexa.framework.runtime.domain.execution.model.NodeRuntime;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultConnectionController implements ConnectionControl {
    private final DefaultRuntimeEngine engine;
    private final Set<String> disabledConnections = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> messageCounters = new ConcurrentHashMap<>();

    public DefaultConnectionController(DefaultRuntimeEngine engine) {
        this.engine = engine;
    }

    public boolean isConnectionDisabled(String sourceNodeId) {
        return disabledConnections.contains(sourceNodeId);
    }

    public void incrementMessageCount(String sourceNodeId) {
        messageCounters.merge(sourceNodeId, 1L, Long::sum);
    }

    @Override
    public void enableConnection(String sourceNodeId) {
        disabledConnections.remove(sourceNodeId);
    }

    @Override
    public void disableConnection(String sourceNodeId) {
        disabledConnections.add(sourceNodeId);
    }

    @Override
    public ConnectionInfo getConnectionInfo(String sourceNodeId) {
        return new ConnectionInfo(
                sourceNodeId,
                "default",
                !disabledConnections.contains(sourceNodeId),
                messageCounters.getOrDefault(sourceNodeId, 0L));
    }

    @Override
    public void injectMessageIntoConnection(String sourceNodeId, RuntimeMessage message) {
        var workspaces = engine.getWorkspaceRuntimes();
        for (WorkspaceRuntime wr : workspaces.values()) {
            for (FlowRuntime flow : wr.flowsById().values()) {
                if (flow.nodeRuntime(sourceNodeId) != null) {
                    engine.injectMessage(wr.workspaceId(), flow.compiledFlow().flowId(), sourceNodeId, message);
                    return;
                }
            }
        }
    }
}
