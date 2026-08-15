package nexa.framework.runtime.api;

import java.time.Duration;

public final class RuntimeConfiguration {

    private final Duration maxExecutionLifetime;

    public RuntimeConfiguration(Duration maxExecutionLifetime) {
        if (maxExecutionLifetime == null || maxExecutionLifetime.isZero() || maxExecutionLifetime.isNegative()) {
            throw new IllegalArgumentException("maxExecutionLifetime must be greater than zero");
        }
        this.maxExecutionLifetime = maxExecutionLifetime;
    }

    public static RuntimeConfiguration defaultConfiguration() {
        return new RuntimeConfiguration(Duration.ofMinutes(1));
    }

    public Duration maxExecutionLifetime() {
        return maxExecutionLifetime;
    }
}

