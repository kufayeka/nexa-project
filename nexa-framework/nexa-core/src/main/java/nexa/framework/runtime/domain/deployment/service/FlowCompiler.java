package nexa.framework.runtime.domain.deployment.service;

import nexa.framework.runtime.domain.deployment.model.CompiledWorkspace;
import nexa.framework.runtime.domain.deployment.model.CompiledConnection;
import nexa.framework.runtime.domain.deployment.model.CompiledFlow;
import nexa.framework.runtime.domain.deployment.model.CompiledNode;
import nexa.framework.runtime.domain.deployment.exception.ValidationException;
import nexa.framework.runtime.domain.workspace.model.ConnectionDefinition;
import nexa.framework.runtime.domain.workspace.model.FlowDefinition;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import nexa.framework.runtime.domain.workspace.model.NodeDefinition;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import nexa.framework.runtime.domain.scripting.api.CompiledScript;
import nexa.framework.runtime.domain.scripting.api.ScriptEngine;
import nexa.framework.runtime.domain.scripting.registry.ScriptEngineRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

public final class FlowCompiler {

    private static final System.Logger LOGGER = System.getLogger(FlowCompiler.class.getName());
    private final FlowValidator validator;
    private final ScriptEngineRegistry scriptEngineRegistry;
    private final ConcurrentMap<String, WorkspaceCompilationSnapshot> workspaceCompilationCache;

    public FlowCompiler(FlowValidator validator) {
        this(validator, loadScriptEngines());
    }

    public FlowCompiler(FlowValidator validator, List<ScriptEngine> scriptEngines) {
        this.validator = validator;
        this.scriptEngineRegistry = new ScriptEngineRegistry(scriptEngines);
        this.workspaceCompilationCache = new ConcurrentHashMap<>();
    }

    public FlowCompiler(FlowValidator validator, ScriptEngineRegistry scriptEngineRegistry) {
        this.validator = validator;
        this.scriptEngineRegistry = scriptEngineRegistry;
        this.workspaceCompilationCache = new ConcurrentHashMap<>();
    }

    public CompiledWorkspace compileWorkspace(WorkspaceDefinition definition) {
        if (definition == null) throw new ValidationException("Workspace definition must not be null");
        if (definition.id() == null || definition.id().isBlank()) throw new ValidationException("Workspace id must not be blank");

        Instant startedAt = Instant.now();
        LOGGER.log(System.Logger.Level.INFO, "Memulai kompilasi workspace id={0} flows={1}", definition.id(), definition.flows().size());

        WorkspaceCompilationSnapshot previousSnapshot = workspaceCompilationCache.get(definition.id());
        Map<String, CompiledFlow> compiledFlows = new LinkedHashMap<>();
        Map<String, String> flowSignatures = new LinkedHashMap<>();

        int flowIndex = 0;
        for (FlowDefinition flowDefinition : definition.flows()) {
            flowIndex++;
            String flowSignature = flowSignature(flowDefinition);
            CompiledFlow compiledFlow = resolveCachedFlow(previousSnapshot, flowDefinition.id(), flowSignature);
            if (compiledFlow == null) compiledFlow = compileFlow(definition.id(), flowDefinition);

            if (compiledFlows.putIfAbsent(flowDefinition.id(), compiledFlow) != null) {
                throw new ValidationException("Workspace " + definition.id() + " contains duplicate flow id " + flowDefinition.id());
            }
            flowSignatures.put(flowDefinition.id(), flowSignature);
        }

        LOGGER.log(System.Logger.Level.INFO, "Selesai kompilasi workspace id={0} took={1}ms", definition.id(), Duration.between(startedAt, Instant.now()).toMillis());
        workspaceCompilationCache.put(definition.id(), new WorkspaceCompilationSnapshot(definition.id(), compiledFlows, flowSignatures));
        return new CompiledWorkspace(definition.id(), definition.enabled(), compiledFlows);
    }

    public CompiledFlow compileFlow(FlowDefinition definition) { return compileFlow("unknown", definition); }

    public CompiledFlow compileFlow(String workspaceId, FlowDefinition definition) {
        validator.validate(definition);

        Map<String, CompiledNode> nodeById = new LinkedHashMap<>();
        for (NodeDefinition nodeDefinition : definition.nodes()) {
            CompiledScript compiledScript = compileNodeScript(workspaceId, definition.id(), nodeDefinition);
            nodeById.put(nodeDefinition.id(), new CompiledNode(
                    nodeDefinition.id(), nodeDefinition.category(), nodeDefinition.type(), nodeDefinition.enabled(),
                    nodeDefinition.inputPolicy(), nodeDefinition.config(), resolveLanguage(nodeDefinition), compiledScript));
        }

        Map<String, Map<String, List<String>>> routes = new LinkedHashMap<>();
        Map<String, CompiledConnection> connectionById = new LinkedHashMap<>();
        for (NodeDefinition nodeDefinition : definition.nodes()) routes.put(nodeDefinition.id(), new LinkedHashMap<>());

        for (ConnectionDefinition connection : definition.connections()) {
            if (connectionById.containsKey(connection.id())) {
                throw new ValidationException("Duplicate connection id " + connection.id() + " in flow " + definition.id());
            }

            boolean enabled = Boolean.TRUE.equals(connection.enabled());
            connectionById.put(connection.id(), new CompiledConnection(
                    connection.id(), connection.sourceNodeId(), connection.sourcePort(), connection.targetNodeId(), enabled));

            Map<String, List<String>> byPort = routes.get(connection.sourceNodeId());
            if (byPort == null) {
                throw new ValidationException("Connection " + connection.id() + " references unknown source node " + connection.sourceNodeId());
            }
            if (!nodeById.containsKey(connection.targetNodeId())) {
                throw new ValidationException("Connection " + connection.id() + " references unknown target node " + connection.targetNodeId());
            }

            if (enabled) {
                byPort.computeIfAbsent(connection.sourcePort(), ignored -> new ArrayList<>()).add(connection.targetNodeId());
            }
        }

        return new CompiledFlow(definition.id(), definition.name(), definition.enabled(), nodeById, routes, connectionById);
    }

    public void invalidateWorkspaceScripts(String workspaceId) {
        workspaceCompilationCache.remove(workspaceId);
        scriptEngineRegistry.invalidateWorkspace(workspaceId);
    }

    public void dispose() {
        workspaceCompilationCache.clear();
        scriptEngineRegistry.dispose();
    }

    private CompiledFlow resolveCachedFlow(WorkspaceCompilationSnapshot snapshot, String flowId, String flowSignature) {
        if (snapshot == null) return null;
        String previousSignature = snapshot.flowSignatures().get(flowId);
        if (!flowSignature.equals(previousSignature)) return null;
        return snapshot.compiledFlows().get(flowId);
    }

    private String flowSignature(FlowDefinition flowDefinition) {
        StringBuilder builder = new StringBuilder();
        builder.append(flowDefinition.id()).append('|').append(flowDefinition.name()).append('|').append(flowDefinition.enabled()).append('|');
        for (NodeDefinition node : flowDefinition.nodes()) {
            builder.append("n:").append(node.id()).append('|').append(node.category()).append('|').append(node.type()).append('|')
                    .append(node.language()).append('|').append(node.enabled()).append('|').append(node.inputPolicy().maxConcurrentExecutions()).append('|');
            appendValue(builder, node.config());
            builder.append('|');
        }
        for (ConnectionDefinition connection : flowDefinition.connections()) {
            builder.append("c:").append(connection.id()).append('|').append(connection.sourceNodeId()).append('|')
                    .append(connection.sourcePort()).append('|').append(connection.targetNodeId()).append('|').append(connection.enabled()).append('|');
        }
        return sha256Hex(builder.toString());
    }

    private void appendValue(StringBuilder builder, Object value) {
        if (value == null) { builder.append("null"); return; }
        if (value instanceof Map<?, ?> map) {
            builder.append('{');
            List<String> keys = new ArrayList<>();
            for (Object key : map.keySet()) keys.add(String.valueOf(key));
            Collections.sort(keys);
            for (String key : keys) { builder.append(key).append('='); appendValue(builder, map.get(key)); builder.append(','); }
            builder.append('}');
            return;
        }
        if (value instanceof List<?> list) {
            builder.append('[');
            for (Object entry : list) { appendValue(builder, entry); builder.append(','); }
            builder.append(']');
            return;
        }
        builder.append(String.valueOf(value));
    }

    private String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) { builder.append(Character.forDigit((b >>> 4) & 0xF, 16)); builder.append(Character.forDigit(b & 0xF, 16)); }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

    private record WorkspaceCompilationSnapshot(String workspaceId, Map<String, CompiledFlow> compiledFlows, Map<String, String> flowSignatures) {
        private WorkspaceCompilationSnapshot {
            compiledFlows = Map.copyOf(new LinkedHashMap<>(compiledFlows));
            flowSignatures = Map.copyOf(new LinkedHashMap<>(flowSignatures));
        }
    }

    private static List<ScriptEngine> loadScriptEngines() {
        List<ScriptEngine> engines = new ArrayList<>();
        ServiceLoader<ScriptEngine> loader = ServiceLoader.load(ScriptEngine.class);
        for (ScriptEngine engine : loader) engines.add(engine);
        return List.copyOf(engines);
    }

    private CompiledScript compileNodeScript(String workspaceId, String flowId, NodeDefinition nodeDefinition) {
        if (nodeDefinition.category() != NodeCategory.EXECUTOR) return null;
        if (nexa.framework.runtime.domain.scripting.registry.PluginRegistry.hasPlugin(nodeDefinition.type())) return null;

        Object scriptRaw = nodeDefinition.config().get("code");
        if (!(scriptRaw instanceof String)) scriptRaw = nodeDefinition.config().get("script");
        if (!(scriptRaw instanceof String scriptSource)) {
            throw new ValidationException("Executor node " + nodeDefinition.id() + " in flow " + flowId + " requires string config.code or config.script");
        }

        String language = resolveLanguage(nodeDefinition);
        if (language == null || language.isBlank()) throw new ValidationException("Executor node " + nodeDefinition.id() + " in flow " + flowId + " requires language");
        ScriptEngine scriptEngine = scriptEngineRegistry.require(language, flowId, nodeDefinition.id());
        LOGGER.log(System.Logger.Level.INFO, "Compile script workspace={0} flow={1} node={2} language={3}", workspaceId, flowId, nodeDefinition.id(), language);
        return scriptEngine.compiler().compile(scriptSource, workspaceId + ":" + flowId + ":" + nodeDefinition.id());
    }

    private String resolveLanguage(NodeDefinition nodeDefinition) {
        if (nodeDefinition.language() != null && !nodeDefinition.language().isBlank()) return nodeDefinition.language();
        return nodeDefinition.type();
    }
}
