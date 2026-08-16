package nexa.framework.runtime.domain.workspace.compiler;

import nexa.framework.runtime.domain.deployment.model.CompiledWorkspace;
import nexa.framework.runtime.domain.deployment.service.FlowCompiler;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/** Adapter that makes the existing FlowCompiler the source of truth for Flow Workspace compilation. */
public final class FlowWorkspaceCompiler {
    private final FlowCompiler flowCompiler;

    public FlowWorkspaceCompiler(FlowCompiler flowCompiler) {
        this.flowCompiler = flowCompiler;
    }

    public CompiledFlowWorkspace compile(WorkspaceDefinition definition, WorkspaceCompilationContext context) {
        if (definition == null) throw new IllegalArgumentException("Flow Workspace definition must not be null");
        CompiledWorkspace compiled = flowCompiler.compileWorkspace(definition);
        Map<String, String> symbols = new LinkedHashMap<>();
        if (context != null) symbols.putAll(context.nodeSymbols());
        return new CompiledFlowWorkspace(definition.id(), compiled, symbols, NexaBytecodeProgram.empty());
    }
}
