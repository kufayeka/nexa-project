package nexa.framework.runtime.api.control;

public interface NexaControlService {
    void start(NexaControlContext context);

    void stop();
}