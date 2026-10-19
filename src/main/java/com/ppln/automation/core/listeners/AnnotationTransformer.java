package com.ppln.automation.core.listeners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

/**
 * Two responsibilities:
 *
 * <ul>
 *   <li>Applies {@link RetryAnalyzer} to every plain TestNG test that does not declare its own.
 *   <li>Adds the groups requested with {@code -Dgroups=...} to Cucumber runner classes, so a
 *       group-filtered TestNG run still executes the runner; the runner then narrows scenarios with
 *       the matching Cucumber tag expression (see {@code core.bdd.CucumberTagFilter} in test
 *       sources).
 * </ul>
 */
public class AnnotationTransformer implements IAnnotationTransformer {

    @Override
    @SuppressWarnings("rawtypes")
    public void transform(
            ITestAnnotation annotation,
            Class testClass,
            Constructor testConstructor,
            Method testMethod) {
        Class<?> owner =
                testClass != null
                        ? testClass
                        : testMethod != null ? testMethod.getDeclaringClass() : null;
        if (owner != null && CucumberRunners.isRunner(owner)) {
            addRequestedGroups(annotation);
            return;
        }
        if (annotation.getRetryAnalyzerClass() == null) {
            annotation.setRetryAnalyzer(RetryAnalyzer.class);
        }
    }

    private static void addRequestedGroups(ITestAnnotation annotation) {
        String requested = System.getProperty("groups", "");
        if (requested.isBlank()) {
            return;
        }
        Set<String> groups = new LinkedHashSet<>(Arrays.asList(annotation.getGroups()));
        Arrays.stream(requested.split(","))
                .map(String::trim)
                .filter(g -> !g.isEmpty())
                .forEach(groups::add);
        annotation.setGroups(groups.toArray(String[]::new));
    }
}
