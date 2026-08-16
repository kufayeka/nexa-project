package nexa.plugin.asset.script;

import java.util.HashSet;
import java.util.Set;

/** Per-execution state for an Asset Manager calculation script. */
public final class AssetScriptContext {
    private final String attributePath;
    private final Self self;
    private final Set<String> trackedReads = new HashSet<>();

    public AssetScriptContext(
        String attributePath,
        Object currentValue,
        Object oldValue,
        Object newValue,
        long timestamp,
        String quality
    ) {
        this.attributePath = attributePath;
        this.self = new Self(currentValue, oldValue, newValue, timestamp, quality);
    }

    public String attributePath() {
        return attributePath;
    }

    public Self self() {
        return self;
    }

    public Set<String> trackedReads() {
        return trackedReads;
    }

    public void recordRead(String path) {
        trackedReads.add(path);
    }

    public record Self(
        Object value,
        Object oldValue,
        Object newValue,
        long timestamp,
        String quality
    ) {}
}
