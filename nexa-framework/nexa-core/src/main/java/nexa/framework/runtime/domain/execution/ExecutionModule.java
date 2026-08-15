package nexa.framework.runtime.domain.execution;

import nexa.framework.runtime.api.OutputConsumer;
import nexa.framework.runtime.api.RuntimeConfiguration;
import nexa.framework.runtime.domain.execution.api.ExecutionService;
import nexa.framework.runtime.domain.execution.controller.DefaultExecutionService;
import nexa.framework.runtime.domain.execution.model.WorkspaceRuntime;
import nexa.framework.runtime.domain.execution.service.RuntimeExecutionService;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ExecutionModule merakit kebutuhan internal untuk domain eksekusi runtime.
 * Bertindak sebagai Composition Root pada tingkat domain.
 */
public final class ExecutionModule {

    private final ExecutionService executionService;
    private final RuntimeExecutionService executionEngine;
    private final ConcurrentMap<String, WorkspaceRuntime> workspaces;

    public ExecutionModule(
            RuntimeConfiguration configuration,
            OutputConsumer outputConsumer,
            nexa.framework.runtime.domain.control.DefaultNodeController nodeController,
            nexa.framework.runtime.domain.control.DefaultConnectionController connectionController,
            nexa.framework.runtime.domain.control.DefaultNexaEventBus eventBus) {
        this.executionEngine = new RuntimeExecutionService(configuration, outputConsumer, nodeController, connectionController, eventBus);
        this.workspaces = new ConcurrentHashMap<>();
        AtomicBoolean runtimeStarted = new AtomicBoolean(false);
        
        this.executionService = new DefaultExecutionService(executionEngine, workspaces, runtimeStarted);
    }

    /**
     * Menyediakan instance ExecutionService yang bertindak sebagai pintu masuk domain.
     */
    public ExecutionService executionService() {
        return executionService;
    }

    /**
     * Mengembalikan engine eksekusi internal agar modul scheduler dapat disuntikkan.
     */
    public RuntimeExecutionService executionEngine() {
        return executionEngine;
    }

    /**
     * Mengembalikan peta workspace runtime aktif.
     */
    public ConcurrentMap<String, WorkspaceRuntime> workspaces() {
        return workspaces;
    }
}
