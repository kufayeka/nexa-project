package nexa.framework.runtime.api.control;

import nexa.framework.runtime.api.control.model.SystemStatus;

public interface RuntimeControl {
    void shutdown();

    void stop();

    void restart();

    SystemStatus getSystemStatus();

    void reloadPlugins();

    void triggerGarbageCollection();

    void resetWorkspaceMetrics(String workspaceId);

    void resetNodeMetrics(String nodeId);
}