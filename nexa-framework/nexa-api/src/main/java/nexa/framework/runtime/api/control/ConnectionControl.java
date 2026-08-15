package nexa.framework.runtime.api.control;

import nexa.framework.runtime.api.control.model.ConnectionInfo;
import nexa.framework.runtime.api.model.RuntimeMessage;

public interface ConnectionControl {
    void enableConnection(String sourceNodeId);

    void disableConnection(String sourceNodeId);

    ConnectionInfo getConnectionInfo(String sourceNodeId);

    void injectMessageIntoConnection(String sourceNodeId, RuntimeMessage message);

    void addConnection(String sourceNodeId, String targetNodeId);

    void removeConnection(String sourceNodeId, String targetNodeId);
}