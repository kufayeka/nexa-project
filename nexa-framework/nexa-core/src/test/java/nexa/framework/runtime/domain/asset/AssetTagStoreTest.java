package nexa.framework.runtime.domain.asset;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetTagStoreTest {
    @Test
    void storesValuesByTypedSlots() {
        var schema = TagStoreSchema.builder()
                .add("running", TagValueType.BOOLEAN)
                .add("speed", TagValueType.INT32)
                .add("temperature", TagValueType.FLOAT64)
                .add("name", TagValueType.STRING)
                .build();
        var store = new TypedTagStore(schema);

        store.setBoolean(schema.find("running"), true);
        store.setInt32(schema.find("speed"), 120);
        store.setFloat64(schema.find("temperature"), 42.5);
        store.setReference(schema.find("name"), "motor-1");

        assertTrue(store.getBoolean(schema.find("running")));
        assertEquals(120, store.getInt32(schema.find("speed")));
        assertEquals(42.5, store.getFloat64(schema.find("temperature")));
        assertEquals("motor-1", store.getReference(schema.find("name")));
    }

    @Test
    void propagatesOnlyAffectedDependencies() {
        var schema = TagStoreSchema.builder()
                .add("a", TagValueType.INT32)
                .add("b", TagValueType.INT32)
                .add("c", TagValueType.INT32)
                .add("d", TagValueType.INT32)
                .build();
        var a = schema.find("a");
        var b = schema.find("b");
        var c = schema.find("c");
        var d = schema.find("d");
        var graph = TagDependencyGraph.builder(4)
                .dependsOn(c.id(), a.id())
                .dependsOn(c.id(), b.id())
                .dependsOn(d.id(), c.id())
                .build();
        var store = new TypedTagStore(schema);
        store.setInt32(a, 2);
        store.setInt32(b, 3);

        var evaluated = new ArrayList<Integer>();
        int count = new TagDependencyEngine(graph).propagate(store, new int[]{a.id(), b.id()}, (id, s) -> {
            evaluated.add(id);
            if (id == c.id()) {
                s.setInt32(c, s.getInt32(a) + s.getInt32(b));
                return true;
            }
            if (id == d.id()) {
                s.setInt32(d, s.getInt32(c) * 2);
                return true;
            }
            return false;
        });

        assertEquals(2, count);
        assertEquals(List.of(c.id(), d.id()), evaluated);
        assertEquals(10, store.getInt32(d));
    }

    @Test
    void rejectsDependencyCycles() {
        var graph = TagDependencyGraph.builder(2)
                .dependsOn(1, 0)
                .dependsOn(0, 1);
        assertThrows(IllegalArgumentException.class, graph::build);
    }
}
