package nexa.framework.runtime.domain.control;

import nexa.framework.runtime.api.control.RuntimeControl;
import nexa.framework.runtime.api.control.model.SystemStatus;
import nexa.framework.runtime.domain.execution.service.DefaultRuntimeEngine;
import nexa.framework.runtime.domain.execution.model.WorkspaceRuntime;
import nexa.framework.runtime.domain.execution.model.FlowRuntime;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public class DefaultRuntimeController implements RuntimeControl {
    private final DefaultRuntimeEngine engine;

    public DefaultRuntimeController(DefaultRuntimeEngine engine) {
        this.engine = engine;
    }

    @Override
    public void shutdown() {
        System.exit(0);
    }

    @Override
    public void stop() {
        engine.stopRuntime();
    }

    @Override
    public void restart() {
        engine.stopRuntime();
        engine.startRuntime();
    }

    @Override
    public SystemStatus getSystemStatus() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = -1.0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            cpuLoad = sunOsBean.getCpuLoad() * 100.0;
        }
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        int activeThreads = Thread.activeCount();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();

        return new SystemStatus(cpuLoad, usedMemory, maxMemory, activeThreads, uptime);
    }

    @Override
    public void reloadPlugins() {
        try {
            Class<?> runnerClass = Class.forName("nexa.framework.NexaStandaloneRunner");
            runnerClass.getMethod("loadDynamicPlugins").invoke(null);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void triggerGarbageCollection() {
        System.gc();
    }

    @Override
    public void resetWorkspaceMetrics(String workspaceId) {
        var workspaces = engine.getWorkspaceRuntimes();
        WorkspaceRuntime wr = workspaces.get(workspaceId);
        if (wr != null) {
            for (FlowRuntime flow : wr.flowsById().values()) {
                flow.statistics().reset();
                for (String nodeId : flow.compiledFlow().nodeById().keySet()) {
                    engine.getNodeController().resetNodeMetrics(nodeId);
                }
            }
        }
    }

    @Override
    public void resetNodeMetrics(String nodeId) {
        var workspaces = engine.getWorkspaceRuntimes();
        for (WorkspaceRuntime wr : workspaces.values()) {
            for (FlowRuntime flow : wr.flowsById().values()) {
                if (flow.nodeRuntime(nodeId) != null) {
                    flow.statistics().reset();
                    engine.getNodeController().resetNodeMetrics(nodeId);
                    return;
                }
            }
        }
    }
}
