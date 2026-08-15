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

import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DefaultRuntimeEngine adalah Composition Root utama yang menyambungkan (wiring)
 * semua Modul Domain (Workspace, Scripting, Deployment, Execution, Scheduler, Statistics)
 * secara eksplisit tanpa framework DI eksternal (Pure DI).
 * 
 * Alur Kerja Perakitan (Wiring Flow):
 * 1. Instansiasi modul daun (WorkspaceModule, ScriptingModule, StatisticsModule)
 * 2. Instansiasi modul Deployment (memerlukan ScriptEngineRegistry dari ScriptingModule)
 * 3. Instansiasi modul Execution (memerlukan konfigurasi global)
 * 4. Instansiasi modul Scheduler (memerlukan ExecutionService untuk men-trigger input)
 * 5. Hubungkan Scheduler inputActivator ke Execution engine untuk memutus siklus dependensi (DIP)
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

    public DefaultRuntimeEngine(RuntimeConfiguration configuration, OutputConsumer outputConsumer) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(outputConsumer, "outputConsumer must not be null");

        // 1. Perakitan Modul Daun / Tanpa dependensi domain lain
        this.workspaceModule = new WorkspaceModule();
        this.scriptingModule = new ScriptingModule();
        this.statisticsModule = new StatisticsModule();

        // 2. Perakitan Modul dengan Constructor Dependency Injection
        this.deploymentModule = new DeploymentModule(scriptingModule.scriptEngineRegistry());
        this.executionModule = new ExecutionModule(configuration, outputConsumer);
        
        // 3. Perakitan Scheduler Module (memerlukan ExecutionService untuk eksekusi input)
        this.executionService = executionModule.executionService();
        this.schedulerModule = new SchedulerModule(executionService, executionModule.executionEngine().scheduler());

        // 4. Inversi Dependensi (DIP) untuk memutus hubungan melingkar (cyclic dependency)
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
                var engine = scriptingModule.scriptEngineRegistry().find(language);
                if (engine != null && engine.compiler() != null) {
                    return engine.compiler().validate(script, errorContainer);
                }
                return false;
            }
        };
    }

    @Override
    public void startRuntime() {
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

    @Override
    public void deploy(WorkspaceDefinition workspaceDefinition) {
        String workspaceId = workspaceDefinition.id();
        
        // Bersihkan resource & node lama jika merupakan proses redeploy
        undeployPlugins(workspaceId);

        // 1. Inisialisasi Resource Plugins
        List<String> resIds = new ArrayList<>();
        if (workspaceDefinition.resources() != null) {
            for (ResourceDefinition resDef : workspaceDefinition.resources()) {
                if (PluginRegistry.hasPlugin(resDef.type())) {
                    try {
                        Class<? extends NexaPlugin> clazz = PluginRegistry.getMeta(resDef.type());
                        NexaPlugin instance = clazz.getDeclaredConstructor().newInstance();
                        if (instance instanceof NexaResourcePlugin resourcePlugin) {
                            resourcePlugin.onInit(resDef.id(), resDef.config(), pluginContext);
                            resourcePlugin.onStart();
                            globalResourceRegistry.registerResource(resDef.id(), resourcePlugin);
                            resIds.add(resDef.id());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Gagal menginisialisasi resource plugin: " + resDef.id(), e);
                    }
                }
            }
        }
        if (!resIds.isEmpty()) {
            workspaceResourceIds.put(workspaceId, resIds);
        }

        // 2. Inisialisasi Node Plugins
        List<String> nodeIds = new ArrayList<>();
        if (workspaceDefinition.flows() != null) {
            for (FlowDefinition flow : workspaceDefinition.flows()) {
                String flowId = flow.id();
                if (flow.nodes() != null) {
                    for (NodeDefinition node : flow.nodes()) {
                        if (PluginRegistry.hasPlugin(node.type())) {
                            try {
                                Class<? extends NexaPlugin> clazz = PluginRegistry.getMeta(node.type());
                                NexaPlugin instance = clazz.getDeclaredConstructor().newInstance();
                                if (instance instanceof nexa.framework.runtime.api.plugin.NexaPluginLifecycle lifecycle) {
                                    lifecycle.onInit(node.id(), node.config(), pluginContext);
                                }

                                if (instance instanceof NexaSourcePlugin sourcePlugin) {
                                    String nodeId = node.id();
                                    sourcePlugin.setEmitter(msg -> trigger(workspaceId, flowId, nodeId, msg));
                                }

                                PluginRegistry.registerInstance(node.id(), instance);
                                nodeIds.add(node.id());
                            } catch (Exception e) {
                                throw new RuntimeException("Gagal menginisialisasi node plugin: " + node.id(), e);
                            }
                        }
                    }
                }
            }
        }
        if (!nodeIds.isEmpty()) {
            workspaceNodeIds.put(workspaceId, nodeIds);
        }

        // Compile menggunakan Deployment domain, kemudian pasang di Execution domain
        CompiledWorkspace compiled = deploymentModule.deploymentService().compile(workspaceDefinition);
        executionService.deploy(compiled);
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
