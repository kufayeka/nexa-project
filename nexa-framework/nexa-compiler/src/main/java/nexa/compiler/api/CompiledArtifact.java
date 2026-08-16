package nexa.compiler.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable compiler deployment artifact with a strict byte-array ownership boundary. */
public record CompiledArtifact(
        String workspaceId,
        CompilationTarget target,
        String compilerVersion,
        Map<String, byte[]> classBytes) {

    public CompiledArtifact {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(compilerVersion, "compilerVersion");
        Objects.requireNonNull(classBytes, "classBytes");

        LinkedHashMap<String, byte[]> owned = new LinkedHashMap<>();
        classBytes.forEach((name, bytes) -> {
            Objects.requireNonNull(name, "classBytes key");
            Objects.requireNonNull(bytes, "classBytes value");
            owned.put(name, bytes.clone());
        });

        // Keep an owned map in the record component. The accessor below never
        // exposes these arrays, so callers cannot mutate artifact state.
        classBytes = java.util.Collections.unmodifiableMap(owned);
    }

    /** Returns a fresh immutable map and a fresh byte[] for every class. */
    @Override
    public Map<String, byte[]> classBytes() {
        LinkedHashMap<String, byte[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : classBytes.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return java.util.Collections.unmodifiableMap(copy);
    }
}
