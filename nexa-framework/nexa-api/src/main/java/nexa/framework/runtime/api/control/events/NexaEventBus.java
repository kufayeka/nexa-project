package nexa.framework.runtime.api.control.events;

import java.util.function.Consumer;

public interface NexaEventBus {
    <T> void publish(String topic, T event);

    <T> void subscribe(String topic, Class<T> eventType, Consumer<T> handler);
}