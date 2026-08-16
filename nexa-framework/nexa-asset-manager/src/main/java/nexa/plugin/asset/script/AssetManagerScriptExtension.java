package nexa.plugin.asset.script;

import nexa.framework.runtime.domain.scripting.internal.nexa.NexaHostObject;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaScriptException;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaRuntime.NexaCallable;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaRuntimeExtension;
import nexa.plugin.asset.resource.AssetManagerResourcePlugin;

import java.math.BigInteger;
import java.util.Map;

/** Host bridge exposed only to the Asset Manager scripting environment. */
public final class AssetManagerScriptExtension implements NexaRuntimeExtension {

    private static final BigInteger UINT64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    @Override
    public Map<String, Object> globals() {
        return Map.of(
            "assetManager", new AssetManagerHostObject(),
            "self", new SelfHostObject(),
            "UInt64", new UInt64HostObject()
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
                    if (!manager.getFlatAttributes().containsKey(path)) {
                        throw new NexaScriptException("Attribute tidak ditemukan: " + path, callLine, callColumn);
                    }
                    AssetScriptContext context = engine.currentContext();
                    if (context != null && AssetManagerResourcePlugin.normalizePath(context.attributePath()).equals(path)) {
                        throw new NexaScriptException("Calculation script tidak boleh menulis ke self attribute sendiri.", callLine, callColumn);
                    }
                    manager.writeInternal(path, arguments.get(1), "GOOD");
                    return true;
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

    /** Exact UInt64 arithmetic for values above signed long range. */
    private static final class UInt64HostObject implements NexaHostObject {
        @Override
        public Object member(String name, int line, int column) {
            return switch (name) {
                case "add" -> (NexaCallable) (runtime, arguments, callLine, callColumn) ->
                        checked(toBigInteger(arguments, 0, callLine, callColumn)
                                .add(toBigInteger(arguments, 1, callLine, callColumn)), callLine, callColumn);
                case "subtract" -> (NexaCallable) (runtime, arguments, callLine, callColumn) ->
                        checked(toBigInteger(arguments, 0, callLine, callColumn)
                                .subtract(toBigInteger(arguments, 1, callLine, callColumn)), callLine, callColumn);
                case "compare" -> (NexaCallable) (runtime, arguments, callLine, callColumn) ->
                        toBigInteger(arguments, 0, callLine, callColumn)
                                .compareTo(toBigInteger(arguments, 1, callLine, callColumn));
                case "parse" -> (NexaCallable) (runtime, arguments, callLine, callColumn) ->
                        checked(new BigInteger(String.valueOf(requireArg(arguments, 0, callLine, callColumn))), callLine, callColumn);
                case "toString" -> (NexaCallable) (runtime, arguments, callLine, callColumn) ->
                        toBigInteger(arguments, 0, callLine, callColumn).toString();
                default -> throw new NexaScriptException("Member UInt64 tidak dikenal: " + name, line, column);
            };
        }

        private static Object requireArg(java.util.List<Object> arguments, int index, int line, int column) {
            if (index >= arguments.size()) {
                throw new NexaScriptException("Argumen UInt64 kurang.", line, column);
            }
            return arguments.get(index);
        }

        private static BigInteger toBigInteger(java.util.List<Object> arguments, int index, int line, int column) {
            Object value = requireArg(arguments, index, line, column);
            if (value instanceof BigInteger b) return b;
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                return BigInteger.valueOf(((Number) value).longValue());
            }
            if (value instanceof String s) {
                try {
                    return new BigInteger(s);
                } catch (NumberFormatException e) {
                    throw new NexaScriptException("Invalid UInt64 value: " + s, line, column);
                }
            }
            throw new NexaScriptException("UInt64 membutuhkan integer/string integer.", line, column);
        }

        private static BigInteger checked(BigInteger value, int line, int column) {
            if (value.signum() < 0 || value.compareTo(UINT64_MAX) > 0) {
                throw new NexaScriptException("UInt64 overflow: " + value, line, column);
            }
            return value;
        }
    }
}
