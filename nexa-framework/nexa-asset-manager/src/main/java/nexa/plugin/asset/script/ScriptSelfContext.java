package nexa.plugin.asset.script;

public final class ScriptSelfContext {
    public static record Self(
        Object value,
        Object oldValue,
        Object newValue,
        long timestamp,
        String quality
    ) {}

    private static final ThreadLocal<Self> context = ThreadLocal.withInitial(() -> null);

    public static void setContext(Self self) {
        context.set(self);
    }

    public static Self getContext() {
        return context.get();
    }

    public static void clearContext() {
        context.remove();
    }
}
