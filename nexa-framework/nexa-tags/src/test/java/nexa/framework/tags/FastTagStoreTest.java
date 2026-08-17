package nexa.framework.tags;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastTagStoreTest {
    @Test
    void readWriteShouldUseTypedSlotsAndExposeTvq() {
        TagRuntime runtime = new TagRuntime(List.of(
                new TagDefinition("speed", TagDataType.FLOAT32, 10.0),
                new TagDefinition("status", TagDataType.STRING, "STOPPED")
        ));
        List<TagValue> events = new CopyOnWriteArrayList<>();
        runtime.onChange(events::add);

        assertEquals(10.0, runtime.read("speed"));
        runtime.write("speed", 12.5);

        assertEquals(12.5, runtime.read("speed"));
        assertEquals(1, events.size());
        assertEquals("speed", events.get(0).path());
        assertEquals(10.0, events.get(0).oldValue());
        assertEquals(12.5, events.get(0).newValue());
        assertEquals(12.5, events.get(0).value());
        assertEquals(TagQuality.GOOD, events.get(0).quality());
        assertTrue(events.get(0).timestamp() > 0);
        runtime.close();
    }
}
