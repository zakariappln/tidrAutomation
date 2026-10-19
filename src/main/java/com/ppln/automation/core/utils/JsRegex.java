package com.ppln.automation.core.utils;

import java.util.regex.Pattern;

/**
 * Playwright evaluates {@link Pattern}s passed to {@code hasURL} / {@code hasValue} / {@code
 * hasText} in the browser as JavaScript regexes, where Java's {@code \Q...\E} quoting from {@link
 * Pattern#quote} is not understood. Use this escaper for any literal that crosses that boundary.
 */
public final class JsRegex {

    private static final Pattern SPECIAL = Pattern.compile("[.*+?^${}()|\\[\\]\\\\/]");

    private JsRegex() {}

    /**
     * Escapes every regex metacharacter so the text matches literally in both Java and JavaScript.
     */
    public static String quote(String literal) {
        return SPECIAL.matcher(literal).replaceAll("\\\\$0");
    }

    public static Pattern literal(String literal) {
        return Pattern.compile(quote(literal));
    }
}
