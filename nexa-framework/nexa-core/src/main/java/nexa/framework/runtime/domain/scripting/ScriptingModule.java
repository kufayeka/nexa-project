package nexa.framework.runtime.domain.scripting;

import nexa.framework.runtime.domain.scripting.api.ScriptEngine;
import nexa.framework.runtime.domain.scripting.registry.ScriptEngineRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * ScriptingModule menginisialisasi dan menyediakan dependensi untuk domain scripting.
 * Bertindak sebagai Composition Root pada tingkat domain.
 */
public final class ScriptingModule {

    private final ScriptEngineRegistry scriptEngineRegistry;

    public ScriptingModule() {
        // Memuat engine kustom dan bawaan via ServiceLoader
        List<ScriptEngine> engines = new ArrayList<>();
        ServiceLoader<ScriptEngine> loader = ServiceLoader.load(ScriptEngine.class);
        for (ScriptEngine engine : loader) {
            engines.add(engine);
        }

        this.scriptEngineRegistry = new ScriptEngineRegistry(List.copyOf(engines));
    }

    /**
     * Menyediakan instance ScriptEngineRegistry untuk pendaftaran dan pencarian engine.
     */
    public ScriptEngineRegistry scriptEngineRegistry() {
        return scriptEngineRegistry;
    }
}
