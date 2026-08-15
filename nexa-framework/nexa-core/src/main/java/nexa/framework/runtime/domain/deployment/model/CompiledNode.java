package nexa.framework.runtime.domain.deployment.model;

import nexa.framework.runtime.domain.workspace.model.InputExecutionPolicyDefinition;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import nexa.framework.runtime.domain.scripting.api.CompiledScript;

import java.util.Map;

public record CompiledNode(
        String id,
        NodeCategory category,
        String type,
        boolean enabled,
        InputExecutionPolicyDefinition inputPolicy,
        Map<String, Object> config,
        String language,
        CompiledScript compiledScript) {
}


