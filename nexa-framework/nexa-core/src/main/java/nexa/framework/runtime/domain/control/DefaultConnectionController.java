package nexa.framework.runtime.domain.control;

import nexa.framework.runtime.api.control.ConnectionControl;
import nexa.framework.runtime.api.control.model.ConnectionInfo;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.execution.service.DefaultRuntimeEngine;
import nexa.framework.runtime.domain.execution.model.WorkspaceRuntime;
import nexa.framework.runtime.domain.execution.model.FlowRuntime;
import nexa.framework.runtime.domain.deployment.model.CompiledConnection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultConnectionController implements ConnectionControl {
    private final DefaultRuntimeEngine engine;
    private final Map<String, Long> messageCounters = new ConcurrentHashMap<>();

    public DefaultConnectionController(DefaultRuntimeEngine engine) { this.engine = engine; }

    @Override
    public void enableConnection(String connectionId) { setConnectionEnabled(connectionId, true); }

    @Override
    public void disableConnection(String connectionId) { setConnectionEnabled(connectionId, false); }

    private void setConnectionEnabled(String connectionId, boolean enabled) {
        validateId(connectionId);
        for (WorkspaceRuntime workspace : engine.getWorkspaceRuntimes().values()) {
            for (FlowRuntime flow : workspace.flowsById().values()) {
                if (flow.compiledFlow().connection(connectionId) == null) continue;
                flow.compiledFlow().setConnectionEnabled(connectionId, enabled);
                flow.refreshRoutes();
                return;
            }
        }
        throw new IllegalArgumentException("Connection not found: " + connectionId);
    }

    @Override
    public ConnectionInfo getConnectionInfo(String connectionId) {
        validateId(connectionId);
        for (WorkspaceRuntime workspace : engine.getWorkspaceRuntimes().values()) {
            for (FlowRuntime flow : workspace.flowsById().values()) {
                CompiledConnection connection = flow.compiledFlow().connection(connectionId);
                if (connection != null) {
                    return new ConnectionInfo(connection.sourceNodeId(), connection.targetNodeId(),
                            connection.enabled(), messageCounters.getOrDefault(connectionId, 0L));
                }
            }
        }
        throw new IllegalArgumentException("Connection not found: " + connectionId);
    }

    @Override
    public void injectMessageIntoConnection(String connectionId, RuntimeMessage message) {
        validateId(connectionId);
        for (WorkspaceRuntime workspace : engine.getWorkspaceRuntimes().values()) {
            for (FlowRuntime flow : workspace.flowsById().values()) {
                CompiledConnection connection = flow.compiledFlow().connection(connectionId);
                if (connection == null) continue;
                if (!workspace.enabled() || !flow.compiledFlow().enabled() || !connection.enabled()) return;
                messageCounters.merge(connectionId, 1L, Long::sum);
                engine.injectMessageIntoConnection(workspace, flow, connection, message);
                return;
            }
        }
        throw new IllegalArgumentException("Connection not found: " + connectionId);
    }

    private static void validateId(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException("connectionId must not be blank");
        }
    }
}
