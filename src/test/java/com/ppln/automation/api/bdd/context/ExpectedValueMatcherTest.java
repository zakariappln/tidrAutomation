package com.ppln.automation.api.bdd.context;

import static com.ppln.automation.api.bdd.context.ExpectedValueMatcher.ABSENT;
import static com.ppln.automation.api.bdd.context.ExpectedValueMatcher.check;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Epic("Framework")
@Feature("Expected-value matchers")
public class ExpectedValueMatcherTest {

    @DataProvider
    public Object[][] matching() {
        return new Object[][] {
            {ABSENT, "is absent"},
            {null, "is null"},
            {"x", "is not empty"},
            {List.of(1), "is not empty"},
            {"3f2504e0-4f89-11d3-9a0c-0305e82c3301", "is a UUID"},
            {"2026-09-21T10:15:30Z", "is an ISO-8601 timestamp"},
            {42, "is a positive integer"},
            {"/api/Branch/42", "matches ^/api/Branch/\\d+$"},
            {"ACTIVE", "is one of ACTIVE, INACTIVE"},
            {List.of(1, 2, 3), "has size 3"},
            {Map.of("a", 1), "has size 1"},
            {"application/json; charset=utf-8", "contains \"application/json\""},
            {List.of("a", "b"), "contains b"},
            {200, "200"},
            {true, "true"},
            {"text", "text"},
        };
    }

    @DataProvider
    public Object[][] failing() {
        return new Object[][] {
            {"value", "is absent"},
            {ABSENT, "is null"},
            {"", "is not empty"},
            {List.of(), "is not empty"},
            {"not-a-uuid", "is a UUID"},
            {"yesterday", "is an ISO-8601 timestamp"},
            {0, "is a positive integer"},
            {1.5, "is a positive integer"},
            {"abc", "matches ^\\d+$"},
            {"DELETED", "is one of ACTIVE, INACTIVE"},
            {List.of(1), "has size 2"},
            {"text/html", "contains \"application/json\""},
            {404, "200"},
        };
    }

    @Test(
            groups = {"unit"},
            dataProvider = "matching",
            description = "Matching values return no reason")
    @Story("Positive matches")
    @Severity(SeverityLevel.CRITICAL)
    public void shouldMatch(Object actual, String expected) {
        assertThat(check(actual, expected)).as("%s should satisfy '%s'", actual, expected).isNull();
    }

    @Test(
            groups = {"unit"},
            dataProvider = "failing",
            description = "Mismatches return a readable reason")
    @Story("Negative matches")
    @Severity(SeverityLevel.CRITICAL)
    public void shouldExplainMismatch(Object actual, String expected) {
        assertThat(check(actual, expected))
                .as("%s should not satisfy '%s'", actual, expected)
                .isNotBlank();
    }
}
