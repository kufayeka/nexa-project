package nexa.framework.runtime.domain.scripting.registry;

import nexa.framework.runtime.domain.scripting.api.ScriptEngine;

import nexa.framework.runtime.domain.deployment.exception.ValidationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ScriptEngineRegistry {

    private final ConcurrentMap<String, ScriptEngine> enginesByLanguage;

    public ScriptEngineRegistry(List<ScriptEngine> engines) {
        this.enginesByLanguage = new ConcurrentHashMap<>();
        for (ScriptEngine engine : engines) {
            register(engine);
        }
    }

    public ScriptEngine require(String language, String flowId, String nodeId) {
        ScriptEngine engine = find(language);
        if (engine == null) {
            throw new ValidationException("Unsupported executor language " + language + " on node " + nodeId
                    + " in flow " + flowId);
        }
        return engine;
    }

    public ScriptEngine find(String language) {
        return enginesByLanguage.get(normalize(language));
    }

    public void register(ScriptEngine engine) {
        String language = normalize(engine.language());
        ScriptEngine previous = enginesByLanguage.putIfAbsent(language, engine);
        if (previous != null) {
            throw new IllegalStateException("Duplicate script engine for language " + engine.language());
        }
    }

    public ScriptEngine unregister(String language) {
        return enginesByLanguage.remove(normalize(language));
    }

    public Map<String, ScriptEngine> snapshot() {
        return Map.copyOf(new LinkedHashMap<>(enginesByLanguage));
    }

    public void invalidateWorkspace(String workspaceId) {
        for (ScriptEngine engine : enginesByLanguage.values()) {
            engine.clearWorkspace(workspaceId);
        }
    }

    public void dispose() {
        for (ScriptEngine engine : uniqueEngines()) {
            engine.dispose();
        }
    }

    private Collection<ScriptEngine> uniqueEngines() {
        return List.copyOf(new ArrayList<>(new java.util.LinkedHashSet<>(enginesByLanguage.values())));
    }

    private String normalize(String language) {
        if (language == null || language.isBlank()) {
            return "";
        }
        return language.toLowerCase(Locale.ROOT);
    }
}


