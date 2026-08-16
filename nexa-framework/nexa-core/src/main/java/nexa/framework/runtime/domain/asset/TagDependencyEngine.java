package nexa.framework.runtime.domain.asset;

import java.util.*;

/** Incremental dependency propagation for compiled tag calculations. */
public final class TagDependencyEngine {
    @FunctionalInterface
    public interface Evaluator {
        boolean evaluate(int tagId, TypedTagStore store);
    }

    private final TagDependencyGraph graph;
    private final BitSet affected = new BitSet();
    private final BitSet upstreamChanged = new BitSet();
    private final int[] indegree;

    public TagDependencyEngine(TagDependencyGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.indegree = new int[graph.tagCount()];
    }

    /**
     * Recalculates only the transitive downstream closure of changed source
     * tags. A derived node is evaluated only after all of its affected
     * predecessors have been visited. Downstream propagation stops when a
     * derived value did not change.
     */
    public int propagate(TypedTagStore store, int[] changedSources, Evaluator evaluator) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(changedSources, "changedSources");
        Objects.requireNonNull(evaluator, "evaluator");
        if (changedSources.length == 0) return 0;

        affected.clear();
        upstreamChanged.clear();
        Arrays.fill(indegree, 0);

        var discovery = new ArrayDeque<Integer>();
        for (int source : changedSources) {
            check(source);
            if (affected.get(source)) continue;
            affected.set(source);
            upstreamChanged.set(source);
            discovery.addLast(source);
        }
        while (!discovery.isEmpty()) {
            int source = discovery.removeFirst();
            for (int target : graph.dependentsOf(source)) {
                if (!affected.get(target)) {
                    affected.set(target);
                    discovery.addLast(target);
                }
            }
        }

        for (int source = affected.nextSetBit(0); source >= 0; source = affected.nextSetBit(source + 1)) {
            for (int target : graph.dependentsOf(source)) {
                if (affected.get(target)) indegree[target]++;
            }
        }

        var ready = new ArrayDeque<Integer>();
        for (int id = affected.nextSetBit(0); id >= 0; id = affected.nextSetBit(id + 1)) {
            if (indegree[id] == 0) ready.addLast(id);
        }

        int evaluated = 0;
        while (!ready.isEmpty()) {
            int id = ready.removeFirst();
            boolean changed = upstreamChanged.get(id);
            if (!isSource(changedSources, id)) {
                if (changed) {
                    changed = evaluator.evaluate(id, store);
                    evaluated++;
                }
            }

            for (int target : graph.dependentsOf(id)) {
                if (!affected.get(target)) continue;
                if (changed) upstreamChanged.set(target);
                if (--indegree[target] == 0) ready.addLast(target);
            }
        }
        return evaluated;
    }

    private static boolean isSource(int[] sources, int id) {
        for (int source : sources) if (source == id) return true;
        return false;
    }

    private void check(int id) {
        if (id < 0 || id >= graph.tagCount()) throw new IndexOutOfBoundsException("Invalid tag slot: " + id);
    }
}
