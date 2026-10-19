package com.ppln.automation.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import com.ppln.automation.api.client.ApiClient;
import com.ppln.automation.api.models.request.LoginRequest;
import com.ppln.automation.core.config.ConfigManager;
import com.ppln.automation.core.config.Credentials;
import com.ppln.automation.core.config.FrameworkConfig;
import com.ppln.automation.core.utils.JsonUtils;
import com.ppln.automation.core.utils.Secrets;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Acquires one JWT per role per JVM via {@code POST auth.base.url + auth.login.path} and caches it.
 * Failures are cached too, so a broken login fails every dependent test fast instead of hammering
 * the auth endpoint. Tokens are never logged or attached.
 *
 * <p>Body encoding is delegated to {@link LoginRequestEncoder}; the token is read from the response
 * with the JSON Pointer in {@code auth.token.json.pointer}.
 */
public final class TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(TokenProvider.class);
    private static final ConcurrentMap<String, Result> CACHE = new ConcurrentHashMap<>();

    private TokenProvider() {}

    private sealed interface Result permits Token, Failure {}

    private record Token(String value) implements Result {}

    private record Failure(RuntimeException cause) implements Result {}

    /** Token for the default credentials ({@code AUTH_USERNAME} / {@code AUTH_PASSWORD}). */
    public static String token() {
        return tokenFor(ConfigManager.DEFAULT_ROLE);
    }

    public static String tokenFor(String role) {
        String key =
                role == null || role.isBlank()
                        ? ConfigManager.DEFAULT_ROLE
                        : role.trim().toLowerCase(Locale.ROOT);
        Result result = CACHE.computeIfAbsent(key, TokenProvider::acquire);
        return switch (result) {
            case Token token -> token.value();
            case Failure failure ->
                    throw new IllegalStateException(
                            "Token acquisition for role '%s' failed earlier in this run"
                                    .formatted(key),
                            failure.cause());
        };
    }

    /** A known-expired token for negative tests; supplied via {@code AUTH_EXPIRED_TOKEN}. */
    public static String expiredToken() {
        return ConfigManager.optional("auth.expired.token")
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "No expired token configured: set AUTH_EXPIRED_TOKEN (auth.expired.token)"));
    }

    /** Drops cached tokens (e.g. after a password rotation test). */
    public static void reset() {
        CACHE.clear();
    }

    private static Result acquire(String role) {
        try {
            return new Token(login(role));
        } catch (RuntimeException e) {
            log.error("Login failed for role '{}': {}", role, e.getMessage());
            return new Failure(e);
        }
    }

    private static String login(String role) {
        FrameworkConfig cfg = ConfigManager.config();
        Credentials credentials = ConfigManager.credentialsFor(role);
        LoginRequestEncoder encoder = LoginRequestEncoder.load();
        String body =
                encoder.body(new LoginRequest(credentials.username(), credentials.password()));

        long started = System.nanoTime();
        try (Playwright playwright = Playwright.create()) {
            APIRequestContext request =
                    ApiClient.newContext(
                            playwright, cfg.effectiveAuthBaseUrl(), AuthHeaders.tenant());
            try {
                RequestOptions options =
                        RequestOptions.create()
                                .setData(body.getBytes(StandardCharsets.UTF_8))
                                .setHeader("Content-Type", encoder.contentType());
                encoder.headers().forEach(options::setHeader);
                APIResponse response = request.post(cfg.authLoginPath(), options);
                String raw = encoder.decodeResponse(response.text());
                if (!response.ok()) {
                    throw new IllegalStateException(
                            "Login for role '%s' returned HTTP %d: %s"
                                    .formatted(
                                            role,
                                            response.status(),
                                            Secrets.mask(abbreviate(raw))));
                }
                JsonNode tokenNode = JsonUtils.readTree(raw).at(cfg.authTokenJsonPointer());
                if (tokenNode.isMissingNode()
                        || tokenNode.isNull()
                        || tokenNode.asText().isBlank()) {
                    throw new IllegalStateException(
                            "Login response has no token at JSON pointer '%s' (auth.token.json.pointer). Body: %s"
                                    .formatted(
                                            cfg.authTokenJsonPointer(),
                                            Secrets.mask(abbreviate(raw))));
                }
                log.info(
                        "Acquired token for role '{}' as user '{}' in {} ms ({} {})",
                        role,
                        credentials.username(),
                        (System.nanoTime() - started) / 1_000_000,
                        encoder.getClass().getSimpleName(),
                        cfg.authLoginPath());
                return tokenNode.asText();
            } finally {
                request.dispose();
            }
        }
    }

    private static String abbreviate(String text) {
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }
}
