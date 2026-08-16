package nexa.framework.runtime.domain.asset;

import java.util.*;

/** Immutable dependency graph compiled from tag references. */
public final class TagDependencyGraph {
    private final int[][] dependents;

    private TagDependencyGraph(int[][] dependents) {
        this.dependents = dependents;
    }

    public int tagCount() { return dependents.length; }

    /** Returns the direct downstream tags of a source slot. */
    public int[] dependentsOf(int sourceId) {
        return dependents[sourceId].clone();
    }

    /**
     * Builds and validates a graph. An edge source -> target means target must
     * be recalculated when source changes. Cycles are rejected at compile time.
     */
    public static Builder builder(int tagCount) { return new Builder(tagCount); }

    public static final class Builder {
        private final int tagCount;
        private final List<List<Integer>> edges;

        public Builder(int tagCount) {
            if (tagCount < 0) throw new IllegalArgumentException("tagCount must be >= 0");
            this.tagCount = tagCount;
            edges = new ArrayList<>(tagCount);
            for (int i = 0; i < tagCount; i++) edges.add(new ArrayList<>());
        }

        public Builder dependsOn(int targetId, int sourceId) {
            check(targetId); check(sourceId);
            if (targetId == sourceId) throw new IllegalArgumentException("Self dependency: " + targetId);
            if (!edges.get(sourceId).contains(targetId)) edges.get(sourceId).add(targetId);
            return this;
        }

        public TagDependencyGraph build() {
            detectCycles();
            int[][] result = new int[tagCount][];
            for (int i = 0; i < tagCount; i++) {
                var list = edges.get(i);
                list.sort(Integer::compareTo);
                result[i] = list.stream().mapToInt(Integer::intValue).toArray();
            }
            return new TagDependencyGraph(result);
        }

        private void detectCycles() {
            byte[] state = new byte[tagCount];
            for (int i = 0; i < tagCount; i++) visit(i, state);
        }

        private void visit(int node, byte[] state) {
            if (state[node] == 1) throw new IllegalArgumentException("Cyclic tag dependency at slot " + node);
            if (state[node] == 2) return;
            state[node] = 1;
            for (int next : edges.get(node)) visit(next, state);
            state[node] = 2;
        }

        private void check(int id) {
            if (id < 0 || id >= tagCount) throw new IndexOutOfBoundsException("Invalid tag slot: " + id);
        }
    }
}
