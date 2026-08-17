package nexa.framework.runtime.api;

import java.util.Map;

public interface NexaCompilerService {
    byte[] compile(String programName, String source, Map<String, Integer> tagSlots);
}
