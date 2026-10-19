package com.ppln.automation.core.utils;

import java.util.regex.Pattern;

/** Redacts credentials and tokens before anything reaches a log or an Allure attachment. */
public final class Secrets {

    private static final Pattern AUTH_HEADER =
            Pattern.compile("(?i)(authorization\\W+)(bearer\\s+)?\\S+");
    private static final Pattern JSON_SECRET_FIELDS =
            Pattern.compile(
                    "(?i)(\"(?:password|newPassword|oldPassword|confirmPassword|token|accessToken|refreshToken|jwt|secret|apiKey)\"\\s*:\\s*\")[^\"]*");

    private Secrets() {}

    /**
     * Masks secret JSON fields only, keeping header text intact. For content that already carries
     * no token by construction, such as a curl command that reads the token from a shell variable.
     */
    public static String maskFields(String text) {
        return text == null ? "" : JSON_SECRET_FIELDS.matcher(text).replaceAll("$1***");
    }

    public static String mask(String text) {
        if (text == null) {
            return "";
        }
        String masked = AUTH_HEADER.matcher(text).replaceAll("$1***");
        return JSON_SECRET_FIELDS.matcher(masked).replaceAll("$1***");
    }
}
