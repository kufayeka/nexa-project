package nexa.framework.runtime.api;

import nexa.framework.runtime.api.model.RuntimeMessage;
import java.util.List;

public interface NexaExecutionContext {
    void send(RuntimeMessage msg);
    void send(String port, RuntimeMessage msg);
    void send(List<String> ports, RuntimeMessage msg);
    Object callHostCapability(String namespace, String name, List<Object> args);

    int readTagInt(int slot);
    long readTagLong(int slot);
    double readTagDouble(int slot);
    Object readTagObject(int slot);

    void writeTagInt(int slot, int value);
    void writeTagLong(int slot, long value);
    void writeTagDouble(int slot, double value);
    void writeTagObject(int slot, Object value);
}
