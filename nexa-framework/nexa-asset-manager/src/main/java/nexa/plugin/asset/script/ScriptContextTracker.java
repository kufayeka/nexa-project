package nexa.plugin.asset.script;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptContextTracker {
    private static final ThreadLocal<String> contextPath = ThreadLocal.withInitial(() -> null);
    private static final ThreadLocal<Set<String>> activeReadPaths = ThreadLocal.withInitial(() -> null);

    public static void setContextPath(String path) {
        contextPath.set(path);
    }

    public static String getContextPath() {
        return contextPath.get();
    }

    public static void clearContext() {
        contextPath.remove();
    }

    public static void startTrackingReads() {
        activeReadPaths.set(ConcurrentHashMap.newKeySet());
    }

    public static Set<String> getTrackedReads() {
        return activeReadPaths.get();
    }

    public static void recordRead(String path) {
        Set<String> tracked = activeReadPaths.get();
        if (tracked != null) {
            tracked.add(path);
        }
    }

    public static void stopTrackingReads() {
        activeReadPaths.remove();
    }
}
