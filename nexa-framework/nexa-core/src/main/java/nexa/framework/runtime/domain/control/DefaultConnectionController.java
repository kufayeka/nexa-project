package nexa.framework.runtime.domain.control;

import nexa.framework.runtime.api.control.ConnectionControl;
import nexa.framework.runtime.api.control.model.ConnectionInfo;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.deployment.model.CompiledConnection;
import nexa.framework.runtime.domain.execution.model.FlowRuntime;
import nexa.framework.runtime.domain.execution.model.WorkspaceRuntime;
import nexa.framework.runtime.domain.execution.service.DefaultRuntimeEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultConnectionController implements ConnectionControl {

    private final DefaultRuntimeEngine engine;
    private final Map<String, Long> messageCounters = new ConcurrentHashMap<>();

    public DefaultConnectionController(DefaultRuntimeEngine engine) {
        this.engine = engine;
    }

    public void incrementMessageCount(String connectionId) {
        messageCounters.merge(connectionId, 1L, Long::sum);
    }

    public boolean isConnectionEnabled(String connectionId) {
        ConnectionRef ref = findConnection(connectionId);
        return ref != null && ref.connection().enabled();
    }

    @Override
    public void enableConnection(String connectionId) {
        setConnectionEnabled(connectionId, true);
    }

    @Override
    public void disableConnection(String connectionId) {
        setConnectionEnabled(connectionId, false);
    }

    @Override
    public ConnectionInfo getConnectionInfo(String connectionId) {
        ConnectionRef ref = findConnection(connectionId);
        if (ref == null) {
            return null;
        }

        CompiledConnection connection = ref.connection();
        return new ConnectionInfo(
                connection.sourceNodeId(),
                connection.targetNodeId(),
                connection.enabled(),
                messageCounters.getOrDefault(connectionId, 0L));
    }

    @Override
    public void injectMessageIntoConnection(String connectionId, RuntimeMessage message) {
        ConnectionRef ref = findConnection(connectionId);
        if (ref == null || !ref.connection().enabled()) {
            return;
        }

        engine.injectMessageIntoConnection(
                ref.workspace().workspaceId(),
                ref.flow().compiledFlow().flowId(),
                connectionId,
                message);
    }

    private void setConnectionEnabled(String connectionId, boolean enabled) {
        if (connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException("connectionId must not be blank");
        }

        ConnectionRef ref = findConnection(connectionId);
        if (ref == null) {
            throw new IllegalArgumentException("Connection " + connectionId + " not found");
        }

        ref.flow().compiledFlow().setConnectionEnabled(connectionId, enabled);
        ref.flow().refreshRoutes();
    }

    private ConnectionRef findConnection(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return null;
        }

        for (WorkspaceRuntime workspace : engine.getWorkspaceRuntimes().values()) {
            for (FlowRuntime flow : workspace.flowsById().values()) {
                CompiledConnection connection = flow.compiledFlow().connection(connectionId);
                if (connection != null) {
                    return new ConnectionRef(workspace, flow, connection);
                }
            }
        }

        return null;
    }

    private record ConnectionRef(
            WorkspaceRuntime workspace,
            FlowRuntime flow,
            CompiledConnection connection) {
    }
}
