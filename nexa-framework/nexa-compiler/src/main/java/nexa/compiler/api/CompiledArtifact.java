package nexa.compiler.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deployment artifact produced by the compiler. The JVM backend emits class bytes;
 * packaging into a JAR is a deployment concern, not the execution format itself.
 *
 * Byte arrays are defensively copied both when the artifact is constructed and
 * when callers read them. This keeps the compiler artifact immutable across the
 * API boundary.
 */
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

        Map<String, byte[]> copy = new LinkedHashMap<>();
        classBytes.forEach((name, bytes) -> {
            Objects.requireNonNull(name, "classBytes key");
            Objects.requireNonNull(bytes, "classBytes value");
            copy.put(name, bytes.clone());
        });
        classBytes = Map.copyOf(copy);
    }

    /** Returns a defensive copy of every class byte array. */
    @Override
    public Map<String, byte[]> classBytes() {
        Map<String, byte[]> copy = new LinkedHashMap<>();
        classBytes.forEach((name, bytes) -> copy.put(name, bytes.clone()));
        return Map.copyOf(copy);
    }
}
