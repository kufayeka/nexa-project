package nexa.framework.runtime.domain.execution.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

public final class ActiveExecution {

    private final ExecutionContext context;
    private final String inputNodeId;
    private final List<Future<?>> futures;

    public ActiveExecution(ExecutionContext context, String inputNodeId) {
        this.context = context;
        this.inputNodeId = inputNodeId;
        this.futures = Collections.synchronizedList(new ArrayList<>());
    }

    public ExecutionContext context() {
        return context;
    }

    public String inputNodeId() {
        return inputNodeId;
    }

    public List<Future<?>> futures() {
        return futures;
    }
}
