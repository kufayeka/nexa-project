package nexa.compiler.api;

import java.util.Map;
import java.util.Objects;

/**
 * Deployment artifact produced by the compiler. The JVM backend emits class bytes;
 * packaging into a JAR is a deployment concern, not the execution format itself.
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
        classBytes = classBytes.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().clone()));
    }
}
