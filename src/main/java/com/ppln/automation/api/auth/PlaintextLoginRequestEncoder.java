package com.ppln.automation.api.auth;

import com.ppln.automation.api.models.request.LoginRequest;

/**
 * Default encoder: sends {@code { "username": ..., "password": ... }} as-is. Replace via
 * ServiceLoader.
 */
public final class PlaintextLoginRequestEncoder implements LoginRequestEncoder {

    @Override
    public Object encode(LoginRequest plain) {
        return plain;
    }
}
