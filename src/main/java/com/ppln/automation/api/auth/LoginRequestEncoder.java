package com.ppln.automation.api.auth;

import com.ppln.automation.api.models.request.LoginRequest;
import com.ppln.automation.core.utils.JsonUtils;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * HOOK POINT for the real login payload. The login endpoint may require an encrypted or otherwise
 * transformed body; that transformation is not known to the framework and must not be guessed.
 *
 * <p>To plug in the real encoding: implement this interface (e.g. {@code
 * EncryptedLoginRequestEncoder}), then register it in {@code
 * src/test/resources/META-INF/services/com.ppln.automation.api.auth.LoginRequestEncoder}. {@link
 * TokenProvider} picks it up via {@link ServiceLoader}; when none is registered the {@link
 * PlaintextLoginRequestEncoder} is used.
 *
 * <p>TODO [AUTH-ENCODING]: provide the production encoder once the login contract is confirmed (see
 * README, "Auth hook points").
 */
public interface LoginRequestEncoder {

    /** Returns the object that will be JSON-serialised as the login request body. */
    Object encode(LoginRequest plain);

    default String contentType() {
        return "application/json";
    }

    /** The exact request body sent. Default: {@link #encode} serialised as JSON. */
    default String body(LoginRequest plain) {
        return JsonUtils.toJson(encode(plain));
    }

    /** Extra headers the login edge requires (e.g. browser-like Origin / Referer). */
    default Map<String, String> headers() {
        return Map.of();
    }

    /** Turns the raw login response into JSON. Default: unchanged. */
    default String decodeResponse(String raw) {
        return raw;
    }

    static LoginRequestEncoder load() {
        return ServiceLoader.load(LoginRequestEncoder.class)
                .findFirst()
                .orElseGet(PlaintextLoginRequestEncoder::new);
    }
}
