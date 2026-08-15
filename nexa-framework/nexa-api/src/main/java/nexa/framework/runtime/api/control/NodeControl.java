package nexa.framework.runtime.api.control;

import nexa.framework.runtime.api.control.model.NodeInfo;
import nexa.framework.runtime.api.control.model.NodeMessageHistory;
import nexa.framework.runtime.api.model.RuntimeMessage;

public interface NodeControl {
    void enableNode(String nodeId);

    void disableNode(String nodeId);

    NodeInfo getNodeInfo(String nodeId);

    NodeMessageHistory getNodeMessages(String nodeId);

    void addBreakpoint(String nodeId);

    void removeBreakpoint(String nodeId);

    void resumeNode(String nodeId);

    void stepNode(String nodeId);

    RuntimeMessage getPausedMessage(String nodeId);
}