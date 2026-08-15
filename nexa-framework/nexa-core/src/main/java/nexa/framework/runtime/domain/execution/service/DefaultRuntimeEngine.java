package nexa.framework.runtime.domain.execution.service;

import nexa.framework.runtime.api.OutputConsumer;
import nexa.framework.runtime.api.RuntimeConfiguration;
import nexa.framework.runtime.api.RuntimeEngine;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.api.plugin.NexaPlugin;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.framework.runtime.api.plugin.NexaResourcePlugin;
import nexa.framework.runtime.api.plugin.NexaSourcePlugin;
import nexa.framework.runtime.domain.workspace.WorkspaceModule;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import nexa.framework.runtime.domain.workspace.model.ResourceDefinition;
import nexa.framework.runtime.domain.workspace.model.FlowDefinition;
import nexa.framework.runtime.domain.workspace.model.NodeDefinition;
import nexa.framework.runtime.domain.scripting.ScriptingModule;
import nexa.framework.runtime.domain.scripting.registry.PluginRegistry;
import nexa.framework.runtime.domain.deployment.DeploymentModule;
import nexa.framework.runtime.domain.deployment.model.CompiledWorkspace;
import nexa.framework.runtime.domain.execution.ExecutionModule;
import nexa.framework.runtime.domain.execution.api.ExecutionService;
import nexa.framework.runtime.domain.scheduler.SchedulerModule;
import nexa.framework.runtime.domain.statistics.StatisticsModule;
import nexa.framework.runtime.domain.statistics.model.RuntimeStatisticsSnapshot;
import nexa.framework.runtime.api.control.events.NexaEventBus;
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

/**
 * DefaultRuntimeEngine adalah Composition Root utama yang menyambungkan
 * (wiring)
 * semua Modul Domain (Workspace, Scripting, Deployment, Execution, Scheduler,
 * Statistics)
 * secara eksplisit tanpa framework DI eksternal (Pure DI).
 * 
 * Alur Kerja Perakitan (Wiring Flow):
 * 1. Instansiasi modul daun (WorkspaceModule, ScriptingModule,
 * StatisticsModule)
 * 2. Instansiasi modul Deployment (memerlukan ScriptEngineRegistry dari
 * ScriptingModule)
 * 3. Instansiasi modul Execution (memerlukan konfigurasi global)
 * 4. Instansiasi modul Scheduler (memerlukan ExecutionService untuk men-trigger
 * input)
 * 5. Hubungkan Scheduler inputActivator ke Execution engine untuk memutus
 * siklus dependensi (DIP)
 */
public final class DefaultRuntimeEngine implements RuntimeEngine {

    private final WorkspaceModule workspaceModule;
    private final ScriptingModule scriptingModule;
    private final DeploymentModule deploymentModule;
    private final ExecutionModule executionModule;
    private final SchedulerModule schedulerModule;
    private final StatisticsModule statisticsModule;

    private final ExecutionService executionService;

    private final GlobalResourceRegistry globalResourceRegistry = new GlobalResourceRegistry();
    private final ConcurrentHashMap<String, List<String>> workspaceResourceIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> workspaceNodeIds = new ConcurrentHashMap<>();
    private final NexaPluginContext pluginContext;

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
        this.controlContext = new DefaultNexaControlContext(
                workspaceController,
                nodeController,
                connectionController,
                runtimeController,
                eventBus);

        // 1. Perakitan Modul Daun / Tanpa dependensi domain lain
        this.workspaceModule = new WorkspaceModule();
        this.scriptingModule = new ScriptingModule();
        this.statisticsModule = new StatisticsModule();

        // 2. Perakitan Modul dengan Constructor Dependency Injection
        this.deploymentModule = new DeploymentModule(scriptingModule.scriptEngineRegistry());
        this.executionModule = new ExecutionModule(configuration, outputConsumer, nodeController, connectionController,
                eventBus);

        // 3. Perakitan Scheduler Module (memerlukan ExecutionService untuk eksekusi
        // input)
        this.executionService = executionModule.executionService();
        this.schedulerModule = new SchedulerModule(executionService, executionModule.executionEngine().scheduler());

        // 4. Inversi Dependensi (DIP) untuk memutus hubungan melingkar (cyclic
        // dependency)
        // Scheduler menyediakan inputActivator untuk dipasang di Execution engine
        this.executionModule.executionEngine().setInputActivator(schedulerModule.inputActivator());

        // 5. Inisialisasi Plugin Context
        this.pluginContext = new NexaPluginContext() {
            @Override
            public Object getSharedResource(String resourceId) {
                NexaResourcePlugin resource = globalResourceRegistry.getResource(resourceId);
                return resource != null ? resource.getNativeClient() : null;
            }

            @Override
            public boolean validateScript(String language, String script, Map<String, Object> errorContainer) {
                return DefaultRuntimeEngine.this.validateScript(language, script, errorContainer);
            }
        };
    }

    @Override
    public void startRuntime() {
        // Start control services via SPI ServiceLoader first so embedded brokers/APIs
        // are available
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

        // Jalankan .onStart() untuk setiap resource plugin eksternal
        workspaceResourceIds.values().forEach(resIds -> {
            for (String resId : resIds) {
                NexaResourcePlugin resource = globalResourceRegistry.getResource(resId);
                if (resource != null) {
                    try {
                        resource.onStart();
                    } catch (Exception e) {
                        System.err.println("[RESOURCE START ERROR] Gagal menjalankan onStart untuk resource: " + resId);
                        e.printStackTrace();
                    }
                }
            }
        });

        // Jalankan .onStart() untuk setiap node plugin eksternal
        workspaceNodeIds.values().forEach(nodeIds -> {
            for (String nodeId : nodeIds) {
                NexaPlugin instance = PluginRegistry.getInstance(nodeId);
                if (instance instanceof nexa.framework.runtime.api.plugin.NexaPluginLifecycle lifecycle) {
                    try {
                        lifecycle.onStart();
                    } catch (Exception e) {
                        System.err.println("[PLUGIN START ERROR] Gagal menjalankan onStart untuk node: " + nodeId);
                        e.printStackTrace();
                    }
                }
            }
        });

        executionService.startRuntime();
    }

    @Override
    public void stopRuntime() {
        // Stop control services first
        for (NexaControlService service : ServiceLoader.load(NexaControlService.class)) {
            try {
                service.stop();
                System.out.println("[CONTROL SERVICE] Stopped control service: " + service.getClass().getName());
            } catch (Exception e) {
                System.err.println(
                        "[CONTROL SERVICE ERROR] Failed to stop control service: " + service.getClass().getName());
                e.printStackTrace();
            }
        }

        executionService.stopRuntime();

        // Hentikan setiap node plugin eksternal
        workspaceNodeIds.values().forEach(nodeIds -> {
            for (String nodeId : nodeIds) {
                PluginRegistry.removeInstance(nodeId);
            }
        });

        // Hentikan setiap resource plugin eksternal
        globalResourceRegistry.clearAll();
        workspaceResourceIds.clear();
        workspaceNodeIds.clear();
    }

    public java.util.concurrent.ConcurrentMap<String, WorkspaceRuntime> getWorkspaceRuntimes() {
        return executionModule.workspaces();
    }

    public DefaultNodeController getNodeController() {
        return nodeController;
    }

    public boolean validateScript(String language, String script, Map<String, Object> errorContainer) {
        var engine = scriptingModule.scriptEngineRegistry().find(language);
        if (engine != null && engine.compiler() != null) {
            return engine.compiler().validate(script, errorContainer);
        }
        return false;
    }

    public void injectMessage(String workspaceId, String flowId, String sourceNodeId, RuntimeMessage message) {
        WorkspaceRuntime wr = executionModule.workspaces().get(workspaceId);
        if (wr != null) {
            FlowRuntime flow = wr.flowsById().get(flowId);
            if (flow != null) {
                executionModule.executionEngine().injectMessage(wr, flow, sourceNodeId, message);
            }
        }
    }

    @Override
    public void deploy(WorkspaceDefinition workspaceDefinition) {
        String workspaceId = workspaceDefinition.id();

        // Bersihkan deployment lama jika ada
        undeployPlugins(workspaceId);

        /*
         * ============================================================
         * 1. COMPILE WORKSPACE TERLEBIH DAHULU
         * ============================================================
         */
        CompiledWorkspace compiled = deploymentModule.deploymentService().compile(workspaceDefinition);

        /*
         * ============================================================
         * 2. DEPLOY KE EXECUTION ENGINE
         * ============================================================
         */
        executionService.deploy(compiled);

        /*
         * ============================================================
         * 3. INITIALIZE RESOURCE PLUGINS
         * ============================================================
         */
        List<String> resIds = new ArrayList<>();

        if (workspaceDefinition.resources() != null) {
            for (ResourceDefinition resDef : workspaceDefinition.resources()) {
                if (!PluginRegistry.hasPlugin(resDef.type())) {
                    continue;
                }

                try {
                    Class<? extends NexaPlugin> clazz = PluginRegistry.getMeta(resDef.type());

                    NexaPlugin instance = clazz.getDeclaredConstructor().newInstance();

                    if (instance instanceof NexaResourcePlugin resourcePlugin) {

                        resourcePlugin.onInit(
                                resDef.id(),
                                resDef.config(),
                                pluginContext);

                        globalResourceRegistry.registerResource(
                                resDef.id(),
                                resourcePlugin);

                        resIds.add(resDef.id());
                    }

                } catch (Exception e) {
                    throw new RuntimeException(
                            "Gagal menginisialisasi resource plugin: "
                                    + resDef.id(),
                            e);
                }
            }
        }

        if (!resIds.isEmpty()) {
            workspaceResourceIds.put(workspaceId, resIds);
        }

        /*
         * ============================================================
         * 4. INITIALIZE NODE PLUGINS
         * ============================================================
         */
        List<String> nodeIds = new ArrayList<>();

        if (workspaceDefinition.flows() != null) {

            for (FlowDefinition flow : workspaceDefinition.flows()) {

                String flowId = flow.id();

                if (flow.nodes() == null) {
                    continue;
                }

                for (NodeDefinition node : flow.nodes()) {

                    if (!PluginRegistry.hasPlugin(node.type())) {
                        continue;
                    }

                    try {

                        Class<? extends NexaPlugin> clazz = PluginRegistry.getMeta(node.type());

                        NexaPlugin instance = clazz.getDeclaredConstructor().newInstance();

                        if (instance instanceof nexa.framework.runtime.api.plugin.NexaPluginLifecycle lifecycle) {

                            lifecycle.onInit(
                                    node.id(),
                                    node.config(),
                                    pluginContext);
                        }

                        if (instance instanceof NexaSourcePlugin sourcePlugin) {

                            String nodeId = node.id();

                            sourcePlugin.setEmitter(
                                    msg -> trigger(
                                            workspaceId,
                                            flowId,
                                            nodeId,
                                            msg));
                        }

                        PluginRegistry.registerInstance(
                                node.id(),
                                instance);

                        nodeIds.add(node.id());

                    } catch (Exception e) {

                        throw new RuntimeException(
                                "Gagal menginisialisasi node plugin: "
                                        + node.id(),
                                e);
                    }
                }
            }
        }

        if (!nodeIds.isEmpty()) {
            workspaceNodeIds.put(workspaceId, nodeIds);
        }

        /*
         * ============================================================
         * 5. START RESOURCES
         * ============================================================
         */
        for (String resId : resIds) {

            NexaResourcePlugin resource = globalResourceRegistry.getResource(resId);

            if (resource != null) {

                try {

                    resource.onStart();

                    System.out.println(
                            "[RESOURCE START] Started resource: "
                                    + resId);

                } catch (Exception e) {

                    System.err.println(
                            "[RESOURCE START ERROR] "
                                    + "Gagal menjalankan onStart untuk resource: "
                                    + resId);

                    e.printStackTrace();
                }
            }
        }

        /*
         * ============================================================
         * 6. START NODE PLUGINS
         * ============================================================
         */
        for (String nodeId : nodeIds) {

            NexaPlugin instance = PluginRegistry.getInstance(nodeId);

            if (instance instanceof nexa.framework.runtime.api.plugin.NexaPluginLifecycle lifecycle) {

                try {

                    lifecycle.onStart();

                    System.out.println(
                            "[PLUGIN START] Started node plugin dynamically: "
                                    + nodeId);

                } catch (Exception e) {

                    System.err.println(
                            "[PLUGIN START ERROR] "
                                    + "Gagal menjalankan onStart untuk node: "
                                    + nodeId);

                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void undeploy(String workspaceId) {
        executionService.disable(workspaceId);
        executionService.undeploy(workspaceId);
        deploymentModule.deploymentService().invalidateWorkspace(workspaceId);
        undeployPlugins(workspaceId);
    }

    private void undeployPlugins(String workspaceId) {
        List<String> nodeIds = workspaceNodeIds.remove(workspaceId);
        if (nodeIds != null) {
            for (String nodeId : nodeIds) {
                PluginRegistry.removeInstance(nodeId);
            }
        }

        List<String> resIds = workspaceResourceIds.remove(workspaceId);
        if (resIds != null) {
            for (String resId : resIds) {
                globalResourceRegistry.removeResource(resId);
            }
        }
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
        System.out.println(
                "[ENGINE TRIGGER] Triggering workspace: " + workspaceId + " flow: " + flowId + " node: " + inputNodeId);
        executionService.trigger(workspaceId, flowId, inputNodeId, message);
    }

    @Override
    public void setNodeEnabled(String workspaceId, String flowId, String nodeId, boolean enabled) {
        executionService.setNodeEnabled(workspaceId, flowId, nodeId, enabled);
    }

    @Override
    public RuntimeStatisticsSnapshot statistics(String workspaceId, String flowId) {
        return executionService.statistics(workspaceId, flowId);
    }
}
