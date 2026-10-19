package com.ppln.automation.core.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** Time helpers shared by placeholders and test data. */
public final class DateUtils {

    private DateUtils() {}

    public static String isoNow() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    }

    /**
     * Shifts an instant by a compact offset such as {@code +1d}, {@code -2h} or {@code +30m}. An
     * empty offset returns the instant unchanged.
     */
    public static Instant shift(Instant base, String offset) {
        if (offset == null || offset.isEmpty()) {
            return base;
        }
        long amount = Long.parseLong(offset.substring(0, offset.length() - 1));
        return switch (offset.charAt(offset.length() - 1)) {
            case 'd' -> base.plus(Duration.ofDays(amount));
            case 'h' -> base.plus(Duration.ofHours(amount));
            case 'm' -> base.plus(Duration.ofMinutes(amount));
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported offset unit in '" + offset + "' (use d, h or m)");
        };
    }
}
