package nexa.framework.runtime.domain.scripting.internal.nexa;

public interface NexaHostObject {

    Object member(String name, int line, int column);
}


