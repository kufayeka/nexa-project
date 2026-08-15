package nexa.framework.runtime.domain.scheduler.helpers;

import java.time.Duration;

public final class DurationParser {

    private DurationParser() {
    }

    public static Duration parseWithMillisecondPrecision(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Duration value must not be blank");
        }

        String trimmed = value.trim().toLowerCase();

        if (trimmed.endsWith("ms")) {
            long amount = parseNumber(trimmed.substring(0, trimmed.length() - 2), value);
            return Duration.ofMillis(amount);
        }

        if (trimmed.endsWith("s")) {
            long amount = parseNumber(trimmed.substring(0, trimmed.length() - 1), value);
            return Duration.ofSeconds(amount);
        }

        if (trimmed.endsWith("m")) {
            long amount = parseNumber(trimmed.substring(0, trimmed.length() - 1), value);
            return Duration.ofMinutes(amount);
        }

        throw new IllegalArgumentException("Unsupported duration format: " + value);
    }

    private static long parseNumber(String candidate, String original) {
        try {
            long value = Long.parseLong(candidate.trim());
            if (value < 1) {
                throw new IllegalArgumentException("Duration must be greater than zero: " + original);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid duration value: " + original, ex);
        }
    }
}


