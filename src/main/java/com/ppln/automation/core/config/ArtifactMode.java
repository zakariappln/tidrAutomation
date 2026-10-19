package com.ppln.automation.core.config;

import java.util.Locale;

/** When to keep traces, videos and screenshots. */
public enum ArtifactMode {
    OFF,
    ON,
    ON_FAILURE,
    RETAIN_ON_FAILURE;

    public static ArtifactMode from(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    public boolean captureAlways() {
        return this == ON || this == RETAIN_ON_FAILURE;
    }

    public boolean captureOnFailure() {
        return this != OFF;
    }
}
