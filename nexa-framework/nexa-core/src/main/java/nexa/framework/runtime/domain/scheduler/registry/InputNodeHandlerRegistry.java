package nexa.framework.runtime.domain.scheduler.registry;

import nexa.framework.runtime.domain.scheduler.api.InputNodeHandler;

import nexa.framework.runtime.domain.deployment.exception.ValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InputNodeHandlerRegistry {

    private final Map<String, InputNodeHandler> handlerByType;

    public InputNodeHandlerRegistry(List<InputNodeHandler> handlers) {
        this.handlerByType = new LinkedHashMap<>();

        for (InputNodeHandler handler : handlers) {
            InputNodeHandler previous = handlerByType.putIfAbsent(handler.nodeType(), handler);
            if (previous != null) {
                throw new ValidationException("Duplicate input node handler for type " + handler.nodeType());
            }
        }
    }

    public InputNodeHandler requireHandler(String nodeType) {
        InputNodeHandler handler = handlerByType.get(nodeType);
        if (handler == null) {
            throw new ValidationException("Unsupported input node type " + nodeType);
        }

        return handler;
    }
}


