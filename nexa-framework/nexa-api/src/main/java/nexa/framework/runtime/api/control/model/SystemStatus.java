package nexa.framework.runtime.api.control.model;

import java.io.Serializable;

public record SystemStatus(
        double cpuUsagePercent,
        long usedMemoryBytes,
        long maxMemoryBytes,
        int activeThreadCount,
        long uptimeMs) implements Serializable {
}