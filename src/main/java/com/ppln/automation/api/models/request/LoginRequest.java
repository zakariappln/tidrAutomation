package com.ppln.automation.api.models.request;

/** Plaintext login payload handed to the {@code LoginRequestEncoder} before it is sent. */
public record LoginRequest(String username, String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=%s, password=***]".formatted(username);
    }
}
