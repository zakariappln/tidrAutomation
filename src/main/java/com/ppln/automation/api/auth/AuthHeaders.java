package com.ppln.automation.api.auth;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Header sets used by the API: {@code Accept} on every call and {@code Authorization: Bearer <jwt>}
 * when authenticated. Add application-specific headers (tenant, routing) here, sourced from config,
 * never hard-coded.
 */
public final class AuthHeaders {

    public static final String AUTHORIZATION = "Authorization";
    public static final String ACCEPT = "Accept";

    private AuthHeaders() {}

    /** Default headers for an unauthenticated context: Accept only. */
    public static Map<String, String> tenant() {
        return Map.of(ACCEPT, "application/json");
    }

    public static Map<String, String> bearer(String token) {
        return Map.of(AUTHORIZATION, "Bearer " + token);
    }

    /**
     * Default headers plus a bearer token for the given role (resolved lazily and cached per run).
     */
    public static Map<String, String> authenticated(String role) {
        var headers = new LinkedHashMap<>(tenant());
        headers.put(AUTHORIZATION, "Bearer " + TokenProvider.tokenFor(role));
        return Map.copyOf(headers);
    }
}
