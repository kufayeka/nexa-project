package nexa.framework.runtime.domain.scripting.api;

import nexa.framework.runtime.api.model.RuntimeMessage;

import java.util.List;
import java.util.Map;

public interface ScriptExecutionResult {

    Map<String, List<RuntimeMessage>> emittedByPort();

    boolean stopped();
}


