package com.ppln.automation.core.config;

import java.util.Arrays;
import java.util.Locale;

/** Target environment; selects {@code config/<env>.properties}. */
public enum Environment {
    STAGING,
    UAT,
    PROD;

    public static Environment from(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Unknown env '%s'. Valid values: %s"
                            .formatted(value, Arrays.toString(values())),
                    e);
        }
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isProduction() {
        return this == PROD;
    }
}
