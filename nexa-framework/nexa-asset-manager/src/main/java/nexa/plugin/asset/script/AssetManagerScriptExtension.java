package nexa.plugin.asset.script;

import nexa.framework.runtime.domain.scripting.internal.nexa.NexaHostObject;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaRuntime.NexaCallable;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaRuntimeExtension;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaScriptException;
import nexa.plugin.asset.resource.AssetManagerResourcePlugin;

import java.util.Map;

/**
 * Host bridge exposed only to the Asset Manager scripting environment.
 */
public final class AssetManagerScriptExtension implements NexaRuntimeExtension {

    @Override
    public Map<String, Object> globals() {
        return Map.of(
            "assetManager", new AssetManagerHostObject(),
            "self", new SelfHostObject()
        );
    }

    private static final class AssetManagerHostObject implements NexaHostObject {

        @Override
        public Object member(String name, int line, int column) {
            AssetManagerResourcePlugin manager = AssetManagerResourcePlugin.getActiveInstance();
            if (manager == null) {
                throw new NexaScriptException("Asset Manager plugin belum aktif atau tidak dapat ditemukan.", line, column);
            }

            AssetScriptingEngine engine = manager.getScriptingEngine();

            return switch (name) {
                case "read" -> (NexaCallable) (runtime, arguments, callLine, callColumn) -> {
                    if (arguments.isEmpty()) {
                        throw new NexaScriptException("Method read() memerlukan 1 argumen path.", callLine, callColumn);
                    }

                    String path = resolvePath(engine, String.valueOf(arguments.getFirst()));
                    AssetScriptContext context = engine.currentContext();
                    if (context != null) {
                        context.recordRead(path);
                    }
                    return manager.read(path);
                };
                case "readVTQ" -> (NexaCallable) (runtime, arguments, callLine, callColumn) -> {
                    if (arguments.isEmpty()) {
                        throw new NexaScriptException("Method readVTQ() memerlukan 1 argumen path.", callLine, callColumn);
                    }

                    String path = resolvePath(engine, String.valueOf(arguments.getFirst()));
                    AssetScriptContext context = engine.currentContext();
                    if (context != null) {
                        context.recordRead(path);
                    }
                    return manager.readVTQ(path);
                };
                case "write" -> (NexaCallable) (runtime, arguments, callLine, callColumn) -> {
                    if (arguments.size() < 2) {
                        throw new NexaScriptException("Method write() memerlukan 2 argumen: path dan value.", callLine, callColumn);
                    }

                    String path = resolvePath(engine, String.valueOf(arguments.getFirst()));
                    return manager.write(path, arguments.get(1));
                };
                default -> throw new NexaScriptException("Member assetManager tidak dikenal: " + name, line, column);
            };
        }

        private String resolvePath(AssetScriptingEngine engine, String path) {
            AssetScriptContext context = engine.currentContext();
            if (context != null) {
                return AssetManagerResourcePlugin.resolvePath(context.attributePath(), path);
            }
            return AssetManagerResourcePlugin.normalizePath(path);
        }
    }
}
