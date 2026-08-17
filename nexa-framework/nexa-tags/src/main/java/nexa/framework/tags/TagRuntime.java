package nexa.framework.tags;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Workspace-owned tag service. Name lookup is outside compiled slot hot paths. */
public final class TagRuntime implements AutoCloseable {
    private final FastTagStore store;
    private final Executor eventExecutor;

    public TagRuntime(List<TagDefinition> definitions) {
        this(definitions, Executors.newVirtualThreadPerTaskExecutor());
    }

    public TagRuntime(List<TagDefinition> definitions, Executor eventExecutor) {
        this.store = new FastTagStore(definitions == null ? List.of() : definitions);
        this.eventExecutor = Objects.requireNonNull(eventExecutor, "eventExecutor");
    }

    public Object read(String path) { return store.read(path); }
    public int readInt(int slot) { return store.readInt(slot); }
    public long readLong(int slot) { return store.readLong(slot); }
    public double readDouble(int slot) { return store.readDouble(slot); }
    public Object readSlot(int slot) { return store.readSlot(slot); }

    public void write(String path, Object value) { write(path, value, TagQuality.GOOD); }
    public void write(String path, Object value, TagQuality quality) {
        store.write(path, value, quality);
    }
    public void writeInt(int slot, int value) { writeSlot(slot, value, TagQuality.GOOD); }
    public void writeLong(int slot, long value) { writeSlot(slot, value, TagQuality.GOOD); }
    public void writeDouble(int slot, double value) { writeSlot(slot, value, TagQuality.GOOD); }
    public void writeObject(int slot, Object value) { writeSlot(slot, value, TagQuality.GOOD); }
    public void writeSlot(int slot, Object value, TagQuality quality) { store.writeSlot(slot, value, quality, true); }

    /** Listener callbacks are queued off the tag write path. */
    public void onWrite(Consumer<TagValue> listener) {
        store.onWrite(event -> eventExecutor.execute(() -> listener.accept(event)));
    }
    public void onChange(Consumer<TagValue> listener) {
        store.onChange(event -> eventExecutor.execute(() -> listener.accept(event)));
    }

    public int slot(String path) { return store.slot(path); }
    public Map<String, Integer> slots() { return store.slots(); }
    public FastTagStore store() { return store; }

    @Override
    public void close() {
        if (eventExecutor instanceof AutoCloseable closeable) {
            try { closeable.close(); } catch (Exception ignored) { }
        }
    }
}
