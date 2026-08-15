package nexa.framework.runtime.domain.control;

import nexa.framework.runtime.api.control.events.NexaEventBus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class DefaultNexaEventBus implements NexaEventBus {
    private final Map<String, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public <T> void publish(String topic, T event) {
        List<Consumer<Object>> topicListeners = listeners.get(topic);
        if (topicListeners != null) {
            for (Consumer<Object> listener : topicListeners) {
                executor.submit(() -> listener.accept(event));
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void subscribe(String topic, Class<T> eventType, Consumer<T> handler) {
        listeners.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                .add(obj -> {
                    if (eventType.isInstance(obj)) {
                        handler.accept((T) obj);
                    }
                });
    }
}