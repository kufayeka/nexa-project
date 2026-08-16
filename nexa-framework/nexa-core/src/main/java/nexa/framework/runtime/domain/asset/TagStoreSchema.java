package nexa.framework.runtime.domain.asset;

import java.util.*;

/** Immutable compiled schema. Runtime lookups use stable numeric slot ids. */
public final class TagStoreSchema {
    private final List<TagSlot> slots;
    private final Map<String, TagSlot> byName;
    private final int[] counts;

    private TagStoreSchema(List<TagSlot> slots) {
        this.slots = List.copyOf(slots);
        var names = new HashMap<String, TagSlot>(slots.size() * 2);
        counts = new int[TagValueType.values().length];
        for (TagSlot slot : slots) {
            if (names.put(slot.name(), slot) != null) {
                throw new IllegalArgumentException("Duplicate tag: " + slot.name());
            }
            counts[slot.type().ordinal()] = Math.max(
                    counts[slot.type().ordinal()], slot.storageIndex() + 1);
        }
        byName = Collections.unmodifiableMap(names);
    }

    public int size() { return slots.size(); }
    public TagSlot slot(int id) { return slots.get(id); }
    public TagSlot find(String name) { return byName.get(name); }
    public int count(TagValueType type) { return counts[type.ordinal()]; }
    public List<TagSlot> slots() { return slots; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<TagSlot> slots = new ArrayList<>();
        private final EnumMap<TagValueType, Integer> next = new EnumMap<>(TagValueType.class);

        public Builder add(String name, TagValueType type) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            if (name.isBlank()) throw new IllegalArgumentException("Tag name must not be blank");
            for (TagSlot existing : slots) {
                if (existing.name().equals(name)) throw new IllegalArgumentException("Duplicate tag: " + name);
            }
            int storageIndex = next.merge(type, 1, Integer::sum) - 1;
            slots.add(new TagSlot(slots.size(), name, type, storageIndex));
            return this;
        }

        public TagStoreSchema build() { return new TagStoreSchema(slots); }
    }
}
