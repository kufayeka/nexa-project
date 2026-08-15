package nexa.framework.runtime.domain.deployment.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CompiledWorkspace(
        String workspaceId,
        boolean enabled,
        Map<String, CompiledFlow> flowsById) {

    public CompiledWorkspace {
        flowsById = Collections.unmodifiableMap(new LinkedHashMap<>(flowsById));
    }
}
