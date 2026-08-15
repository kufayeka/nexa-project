package nexa.framework.runtime.domain.scheduler;

import nexa.framework.runtime.domain.execution.api.ExecutionService;
import nexa.framework.runtime.domain.execution.api.InputActivator;
import nexa.framework.runtime.domain.scheduler.registry.InputNodeHandlerRegistry;
import nexa.framework.runtime.domain.scheduler.service.InputActivationService;
import nexa.framework.runtime.domain.scheduler.service.ManualInputNodeHandler;
import nexa.framework.runtime.domain.scheduler.service.TimedTriggerInputNodeHandler;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

/**
 * SchedulerModule merakit kebutuhan internal untuk pemicu input node.
 * Bertindak sebagai Composition Root pada tingkat domain.
 */
public final class SchedulerModule {

    private final InputActivator inputActivator;

    public SchedulerModule(
            ExecutionService executionService,
            ScheduledExecutorService scheduler) {
        InputNodeHandlerRegistry handlerRegistry = new InputNodeHandlerRegistry(List.of(
                new ManualInputNodeHandler(),
                new TimedTriggerInputNodeHandler()
        ));
        this.inputActivator = new InputActivationService(handlerRegistry, scheduler, executionService);
    }

    /**
     * Menyediakan instance InputActivator untuk domain execution.
     */
    public InputActivator inputActivator() {
        return inputActivator;
    }
}
