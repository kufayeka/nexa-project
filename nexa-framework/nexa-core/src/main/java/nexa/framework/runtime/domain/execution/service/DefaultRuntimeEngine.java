package nexa.framework.runtime.domain.execution.service;

import nexa.framework.runtime.api.OutputConsumer;
import nexa.framework.runtime.api.RuntimeConfiguration;
import nexa.framework.runtime.api.RuntimeEngine;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.workspace.WorkspaceModule;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import nexa.framework.runtime.domain.workspace.model.FlowDefinition;
import nexa.framework.runtime.domain.deployment.DeploymentModule;
import nexa.framework.runtime.domain.deployment.model.CompiledWorkspace;
import nexa.framework.runtime.domain.deployment.model.CompiledConnection;
import nexa.framework.runtime.domain.execution.ExecutionModule;
import nexa.framework.runtime.domain.execution.api.ExecutionService;
import nexa.framework.runtime.domain.scheduler.SchedulerModule;
import nexa.framework.runtime.domain.statistics.StatisticsModule;
import nexa.framework.runtime.domain.statistics.model.RuntimeStatisticsSnapshot;
import nexa.framework.runtime.api.control.NexaControlContext;
import nexa.framework.runtime.api.control.NexaControlService;
import nexa.framework.runtime.domain.control.DefaultNodeController;
import nexa.framework.runtime.domain.control.DefaultNexaEventBus;
import nexa.framework.runtime.domain.control.DefaultWorkspaceController;
import nexa.framework.runtime.domain.control.DefaultConnectionController;
import nexa.framework.runtime.domain.control.DefaultRuntimeController;
import nexa.framework.runtime.domain.control.DefaultNexaControlContext;
import nexa.framework.runtime.domain.execution.model.WorkspaceRuntime;
import nexa.framework.runtime.domain.execution.model.FlowRuntime;
import java.util.ServiceLoader;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultRuntimeEngine implements RuntimeEngine {
    private final WorkspaceModule workspaceModule;
    private final DeploymentModule deploymentModule;
    private final ExecutionModule executionModule;
    private final SchedulerModule schedulerModule;
    private final StatisticsModule statisticsModule;
    private final ExecutionService executionService;
    private final DefaultNexaEventBus eventBus;
    private final DefaultNodeController nodeController;
    private final DefaultWorkspaceController workspaceController;
    private final DefaultConnectionController connectionController;
    private final DefaultRuntimeController runtimeController;
    private final DefaultNexaControlContext controlContext;

    public DefaultRuntimeEngine(RuntimeConfiguration configuration, OutputConsumer outputConsumer) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(outputConsumer, "outputConsumer must not be null");
        this.eventBus = new DefaultNexaEventBus();
        this.nodeController = new DefaultNodeController();
        this.connectionController = new DefaultConnectionController(this);
        this.workspaceController = new DefaultWorkspaceController(this);
        this.runtimeController = new DefaultRuntimeController(this);
        this.controlContext = new DefaultNexaControlContext(workspaceController, nodeController, connectionController,
                runtimeController, eventBus);
        this.workspaceModule = new WorkspaceModule();
        this.statisticsModule = new StatisticsModule();
        this.deploymentModule = new DeploymentModule();
        this.executionModule = new ExecutionModule(configuration, outputConsumer, nodeController, connectionController,
                eventBus);
        this.executionService = executionModule.executionService();
        this.schedulerModule = new SchedulerModule(executionService, executionModule.executionEngine().scheduler());
        this.executionModule.executionEngine().setInputActivator(schedulerModule.inputActivator());
    }

    @Override
    public void startRuntime() {
        for (NexaControlService service : ServiceLoader.load(NexaControlService.class)) {
            try {
                service.start(controlContext);
                System.out.println("[CONTROL SERVICE] Started control service: " + service.getClass().getName());
            } catch (Exception e) {
                System.err.println(
                        "[CONTROL SERVICE ERROR] Failed to start control service: " + service.getClass().getName());
                e.printStackTrace();
            }
        }
        executionService.startRuntime();
    }

    @Override
    public void stopRuntime() {
        System.out.println("[RUNTIME] Shutting down...");
        for (WorkspaceRuntime workspace : executionModule.workspaces().values()) {
            try {
                if (workspace.enabled()) {
                    System.out.println("[RUNTIME] Disabling workspace: " + workspace.workspaceId());
                    executionService.disable(workspace.workspaceId());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            executionService.stopRuntime();
        } catch (Exception e) {
            System.err.println("[RUNTIME] Failed to stop execution engine");
            e.printStackTrace();
        }
        System.out.println("[RUNTIME] Runtime stopped cleanly.");
    }

    public java.util.concurrent.ConcurrentMap<String, WorkspaceRuntime> getWorkspaceRuntimes() {
        return executionModule.workspaces();
    }

    public DefaultNodeController getNodeController() {
        return nodeController;
    }

    public void injectMessage(String workspaceId, String flowId, String sourceNodeId, RuntimeMessage message) {
        WorkspaceRuntime wr = executionModule.workspaces().get(workspaceId);
        if (wr != null) {
            FlowRuntime flow = wr.flowsById().get(flowId);
            if (flow != null)
                executionModule.executionEngine().injectMessage(wr, flow, sourceNodeId, message);
        }
    }

    public void injectMessageIntoConnection(WorkspaceRuntime workspace, FlowRuntime flow, CompiledConnection connection,
            RuntimeMessage message) {
        executionModule.executionEngine().injectMessageIntoConnection(workspace, flow, connection, message);
    }

    @Override
    public void deploy(WorkspaceDefinition workspaceDefinition) {
        CompiledWorkspace compiled = deploymentModule.deploymentService().compile(workspaceDefinition);
        executionService.deploy(compiled);
    }

    @Override
    public void undeploy(String workspaceId) {
        executionService.disable(workspaceId);
        executionService.undeploy(workspaceId);
        deploymentModule.deploymentService().invalidateWorkspace(workspaceId);
    }

    @Override
    public void disable(String workspaceId) {
        executionService.disable(workspaceId);
    }

    @Override
    public void enable(String workspaceId) {
        executionService.enable(workspaceId);
    }

    @Override
    public void trigger(String workspaceId, String flowId, String inputNodeId, RuntimeMessage message) {
        executionService.trigger(workspaceId, flowId, inputNodeId, message);
    }

    @Override
    public void setNodeEnabled(String workspaceId, String flowId, String nodeId, boolean enabled) {
        executionService.setNodeEnabled(workspaceId, flowId, nodeId, enabled);
    }

    public boolean validateScript(String language, String script, Map<String, Object> errorContainer) {
        return true;
    }

    @Override
    public RuntimeStatisticsSnapshot statistics(String workspaceId, String flowId) {
        return executionService.statistics(workspaceId, flowId);
    }
}
