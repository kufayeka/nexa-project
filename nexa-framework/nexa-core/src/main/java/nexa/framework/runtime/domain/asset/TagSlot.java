package nexa.framework.runtime.domain.asset;

import java.util.Objects;

/** Compile-time stable location of a tag in the typed store. */
public record TagSlot(int id, String name, TagValueType type, int storageIndex) {
    public TagSlot {
        if (id < 0) throw new IllegalArgumentException("id must be >= 0");
        if (storageIndex < 0) throw new IllegalArgumentException("storageIndex must be >= 0");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
