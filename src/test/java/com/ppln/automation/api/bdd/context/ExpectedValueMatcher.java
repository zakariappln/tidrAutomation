package com.ppln.automation.api.bdd.context;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Closed set of matchers from the Gherkin style guide. {@link #check} returns {@code null} when the
 * value matches, otherwise a human-readable failure reason.
 */
public final class ExpectedValueMatcher {

    /** Sentinel: the key does not exist in the document. */
    public static final Object ABSENT =
            new Object() {
                @Override
                public String toString() {
                    return "<absent>";
                }
            };

    private ExpectedValueMatcher() {}

    public static String check(Object actual, String expected) {
        String e = expected.trim();
        if (e.equals("is absent")) {
            return actual == ABSENT ? null : "expected key to be absent but was <" + actual + ">";
        }
        if (actual == ABSENT) {
            return "key is absent";
        }
        if (e.equals("is null")) {
            return actual == null ? null : "expected null but was <" + actual + ">";
        }
        if (e.equals("is not empty")) {
            return isNotEmpty(actual) ? null : "expected non-empty but was <" + actual + ">";
        }
        if (e.equals("is a UUID")) {
            return tryParse(() -> UUID.fromString(str(actual)), "UUID", actual);
        }
        if (e.equals("is an ISO-8601 timestamp")) {
            return tryParse(() -> Instant.parse(str(actual)), "ISO-8601 timestamp", actual);
        }
        if (e.equals("is a positive integer")) {
            return actual instanceof Number n && n.longValue() > 0 && n.doubleValue() % 1 == 0
                    ? null
                    : "expected positive integer but was <" + actual + ">";
        }
        if (e.startsWith("matches ")) {
            String regex = e.substring(8).trim();
            return Pattern.matches(regex, str(actual))
                    ? null
                    : "<" + actual + "> does not match /" + regex + "/";
        }
        if (e.startsWith("is one of ")) {
            List<String> set = Arrays.stream(e.substring(10).split(",")).map(String::trim).toList();
            return set.contains(str(actual)) ? null : "<" + actual + "> not in " + set;
        }
        if (e.startsWith("has size ")) {
            int n = Integer.parseInt(e.substring(9).trim());
            int size =
                    actual instanceof Collection<?> c
                            ? c.size()
                            : actual instanceof Map<?, ?> m ? m.size() : -1;
            return size == n ? null : "expected size " + n + " but was " + size;
        }
        if (e.startsWith("contains ")) {
            String needle = unquote(e.substring(9));
            boolean ok =
                    actual instanceof Collection<?> c
                            ? c.stream().map(String::valueOf).anyMatch(needle::equals)
                            : str(actual).contains(needle);
            return ok ? null : "<" + actual + "> does not contain <" + needle + ">";
        }
        // literal: numbers/booleans compared by string form so tables can say 400 or true
        return e.equals(str(actual)) ? null : "expected <" + e + "> but was <" + actual + ">";
    }

    private static boolean isNotEmpty(Object actual) {
        if (actual == null) {
            return false;
        }
        if (actual instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (actual instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        return !actual.toString().isBlank();
    }

    private static String str(Object o) {
        return String.valueOf(o);
    }

    private static String unquote(String s) {
        String t = s.trim();
        return t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")
                ? t.substring(1, t.length() - 1)
                : t;
    }

    private static String tryParse(Runnable parser, String what, Object actual) {
        try {
            parser.run();
            return null;
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            return "<" + actual + "> is not a " + what;
        }
    }
}
