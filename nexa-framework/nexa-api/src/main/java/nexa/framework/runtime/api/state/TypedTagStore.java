package nexa.framework.runtime.api.state;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TypedTagStore {
    private static final VarHandle INT_ARRAY = MethodHandles.arrayElementVarHandle(int[].class);
    private static final VarHandle LONG_ARRAY = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle DOUBLE_ARRAY = MethodHandles.arrayElementVarHandle(double[].class);
    private static final VarHandle OBJECT_ARRAY = MethodHandles.arrayElementVarHandle(Object[].class);

    private final int[] intValues;
    private final long[] longValues;
    private final double[] doubleValues;
    private final Object[] objectValues;

    // Listeners for slot events
    private final List<TagListener> intListeners = new CopyOnWriteArrayList<>();
    private final List<TagListener> longListeners = new CopyOnWriteArrayList<>();
    private final List<TagListener> doubleListeners = new CopyOnWriteArrayList<>();
    private final List<TagListener> objectListeners = new CopyOnWriteArrayList<>();

    public interface TagListener {
        void onWrite(int slot, Object oldValue, Object newValue);
        void onChange(int slot, Object oldValue, Object newValue);
    }

    public TypedTagStore(int intSize, int longSize, int doubleSize, int objectSize) {
        this.intValues = new int[intSize];
        this.longValues = new long[longSize];
        this.doubleValues = new double[doubleSize];
        this.objectValues = new Object[objectSize];
    }

    public void addTagListener(TagListener listener) {
        Objects.requireNonNull(listener);
        intListeners.add(listener);
        longListeners.add(listener);
        doubleListeners.add(listener);
        objectListeners.add(listener);
    }

    // --- INT ---
    public int readInt(int slot) {
        return (int) INT_ARRAY.getVolatile(intValues, slot);
    }

    public void writeInt(int slot, int value) {
        int old = (int) INT_ARRAY.getVolatile(intValues, slot);
        INT_ARRAY.setVolatile(intValues, slot, value);
        
        // Notify listeners
        for (TagListener listener : intListeners) {
            listener.onWrite(slot, old, value);
            if (old != value) {
                listener.onChange(slot, old, value);
            }
        }
    }

    // --- LONG ---
    public long readLong(int slot) {
        return (long) LONG_ARRAY.getVolatile(longValues, slot);
    }

    public void writeLong(int slot, long value) {
        long old = (long) LONG_ARRAY.getVolatile(longValues, slot);
        LONG_ARRAY.setVolatile(longValues, slot, value);
        
        // Notify listeners
        for (TagListener listener : longListeners) {
            listener.onWrite(slot, old, value);
            if (old != value) {
                listener.onChange(slot, old, value);
            }
        }
    }

    // --- DOUBLE ---
    public double readDouble(int slot) {
        return (double) DOUBLE_ARRAY.getVolatile(doubleValues, slot);
    }

    public void writeDouble(int slot, double value) {
        double old = (double) DOUBLE_ARRAY.getVolatile(doubleValues, slot);
        DOUBLE_ARRAY.setVolatile(doubleValues, slot, value);
        
        // Notify listeners
        for (TagListener listener : doubleListeners) {
            listener.onWrite(slot, old, value);
            if (Double.compare(old, value) != 0) {
                listener.onChange(slot, old, value);
            }
        }
    }

    // --- OBJECT ---
    public Object readObject(int slot) {
        return OBJECT_ARRAY.getVolatile(objectValues, slot);
    }

    public void writeObject(int slot, Object value) {
        Object old = OBJECT_ARRAY.getVolatile(objectValues, slot);
        OBJECT_ARRAY.setVolatile(objectValues, slot, value);
        
        // Notify listeners
        for (TagListener listener : objectListeners) {
            listener.onWrite(slot, old, value);
            if (!Objects.equals(old, value)) {
                listener.onChange(slot, old, value);
            }
        }
    }
}
