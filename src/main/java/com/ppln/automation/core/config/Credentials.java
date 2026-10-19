package com.ppln.automation.core.config;

/** Username/password pair. {@link #toString()} never reveals the password. */
public record Credentials(String username, String password) {

    @Override
    public String toString() {
        return "Credentials[username=%s, password=***]".formatted(username);
    }
}
