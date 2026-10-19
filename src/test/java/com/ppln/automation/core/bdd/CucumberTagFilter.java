package com.ppln.automation.core.bdd;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Keeps one switch for users: {@code -Dgroups=smoke,branches} filters plain TestNG tests by group
 * and Cucumber scenarios by tag ({@code (@smoke or @branches) and not @wip}). Runners call {@link
 * #apply()} from a static initialiser; an explicit {@code -Dcucumber.filter.tags} always wins.
 */
public final class CucumberTagFilter {

    public static final String TAGS_PROPERTY = "cucumber.filter.tags";

    /** Never run by default: unfinished glue, environment-blocked, and decided out of scope. */
    public static final String DEFAULT_EXCLUSIONS =
            "not @wip and not @blocked-env and not @out-of-scope and not @known-defect";

    private CucumberTagFilter() {}

    public static void apply() {
        String groups = System.getProperty("groups", "");
        if (groups.isBlank() || System.getProperty(TAGS_PROPERTY) != null) {
            return;
        }
        System.setProperty(TAGS_PROPERTY, toTagExpression(groups));
    }

    static String toTagExpression(String groups) {
        String expression =
                Arrays.stream(groups.split(","))
                        .map(String::trim)
                        .filter(g -> !g.isEmpty())
                        .map(g -> "@" + g)
                        .collect(Collectors.joining(" or "));
        return "(" + expression + ") and " + DEFAULT_EXCLUSIONS;
    }
}
