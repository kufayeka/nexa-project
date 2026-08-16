package nexa.compiler.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable compiler deployment artifact with strict byte-array ownership. */
public final class CompiledArtifact {
    private final String workspaceId;
    private final CompilationTarget target;
    private final String compilerVersion;
    private final Map<String, byte[]> classBytes;

    public CompiledArtifact(
            String workspaceId,
            CompilationTarget target,
            String compilerVersion,
            Map<String, byte[]> classBytes) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.target = Objects.requireNonNull(target, "target");
        this.compilerVersion = Objects.requireNonNull(compilerVersion, "compilerVersion");
        Objects.requireNonNull(classBytes, "classBytes");

        LinkedHashMap<String, byte[]> owned = new LinkedHashMap<>();
        classBytes.forEach((name, bytes) -> {
            Objects.requireNonNull(name, "classBytes key");
            Objects.requireNonNull(bytes, "classBytes value");
            owned.put(name, bytes.clone());
        });
        this.classBytes = java.util.Collections.unmodifiableMap(owned);
    }

    public String workspaceId() { return workspaceId; }
    public CompilationTarget target() { return target; }
    public String compilerVersion() { return compilerVersion; }

    /** Returns a fresh map and fresh byte array for every class on every read. */
    public Map<String, byte[]> classBytes() {
        LinkedHashMap<String, byte[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : classBytes.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return java.util.Collections.unmodifiableMap(copy);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CompiledArtifact that)) return false;
        return workspaceId.equals(that.workspaceId)
                && target.equals(that.target)
                && compilerVersion.equals(that.compilerVersion)
                && classBytesEqual(classBytes, that.classBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(workspaceId, target, compilerVersion);
        for (var entry : classBytes.entrySet()) {
            result = 31 * result + Objects.hash(entry.getKey(), java.util.Arrays.hashCode(entry.getValue()));
        }
        return result;
    }

    private static boolean classBytesEqual(Map<String, byte[]> a, Map<String, byte[]> b) {
        if (!a.keySet().equals(b.keySet())) return false;
        for (String key : a.keySet()) {
            if (!java.util.Arrays.equals(a.get(key), b.get(key))) return false;
        }
        return true;
    }
}