package nexa.plugin.asset.script;

import nexa.plugin.asset.resource.AssetManagerResourcePlugin;

/**
 * Compatibility facade for existing Asset Manager call sites.
 * The actual compiler/runtime/cache now lives in {@link AssetScriptingEngine}.
 */
@Deprecated(forRemoval = true)
public final class AssetScriptExecutor {
    private AssetScriptExecutor() {
    }

    public static Object executeCalculation(
        String script,
        String attributePath,
        Object currentValue,
        Object oldValue,
        Object newValue
    ) {
        AssetManagerResourcePlugin manager = AssetManagerResourcePlugin.getActiveInstance();
        if (manager == null) {
            throw new IllegalStateException("Asset Manager plugin belum aktif atau tidak dapat ditemukan.");
        }

        AssetScriptingEngine engine = AssetScriptingEngine.forManager(manager);
        AssetManagerResourcePlugin.setInsideCalculationScript(true);
        try {
            return engine.executeCalculation(
                manager,
                script,
                attributePath,
                currentValue,
                oldValue,
                newValue
            );
        } finally {
            AssetManagerResourcePlugin.setInsideCalculationScript(false);
        }
    }
}
