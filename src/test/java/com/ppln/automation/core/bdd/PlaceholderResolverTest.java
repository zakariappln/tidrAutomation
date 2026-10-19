package com.ppln.automation.core.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.testng.annotations.Test;

@Epic("Framework")
@Feature("Placeholder resolution")
public class PlaceholderResolverTest {

    private static PlaceholderResolver resolver(Map<String, String> vars) {
        return new PlaceholderResolver(vars);
    }

    @Test(
            groups = {"unit"},
            description = "Stored variables are substituted inside text")
    @Story("Stored variables")
    @Severity(SeverityLevel.CRITICAL)
    public void shouldSubstituteStoredVariables() {
        var vars = new HashMap<>(Map.of("branchId", "42"));
        assertThat(resolver(vars).resolve("/api/Branch/{branchId}")).isEqualTo("/api/Branch/42");
    }

    @Test(
            groups = {"unit"},
            description = "Generated values are stable within a scenario")
    @Story("Generated values")
    @Severity(SeverityLevel.CRITICAL)
    public void shouldCacheGeneratedValuesPerScenario() {
        var r = resolver(new HashMap<>());
        assertThat(r.resolve("{uniqueEmail}"))
                .isEqualTo(r.resolve("{uniqueEmail}"))
                .endsWith("@example.com");
        assertThat(r.resolve("{uuid}")).isEqualTo(r.resolve("{uuid}")).hasSize(36);
        assertThat(r.resolve("{uniqueString}")).startsWith("qa-");
    }

    @Test(
            groups = {"unit"},
            description = "{now+1d} is an ISO instant one day ahead")
    @Story("Time placeholders")
    @Severity(SeverityLevel.NORMAL)
    public void shouldShiftNowByOffset() {
        Instant shifted = Instant.parse(resolver(new HashMap<>()).resolve("{now+1d}"));
        assertThat(shifted).isAfter(Instant.now().plusSeconds(23 * 3600));
    }

    @Test(
            groups = {"unit"},
            description = "{string of N chars} expands to N characters")
    @Story("Length placeholders")
    @Severity(SeverityLevel.NORMAL)
    public void shouldExpandStringOfChars() {
        assertThat(resolver(new HashMap<>()).resolve("{string of 12 chars}")).hasSize(12);
    }

    @Test(
            groups = {"unit"},
            description = "Unknown placeholders fail with the list of known ones")
    @Story("Diagnostics")
    @Severity(SeverityLevel.NORMAL)
    public void shouldFailOnUnknownPlaceholder() {
        assertThatThrownBy(() -> resolver(new HashMap<>()).resolve("{missing}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{missing}")
                .hasMessageContaining("baseUrl");
    }

    @Test(
            groups = {"unit"},
            description = "Text without placeholders is returned unchanged")
    @Story("Pass-through")
    @Severity(SeverityLevel.MINOR)
    public void shouldReturnPlainTextUnchanged() {
        assertThat(resolver(new HashMap<>()).resolve("plain text")).isEqualTo("plain text");
        assertThat(resolver(new HashMap<>()).resolve(null)).isNull();
    }

    @Test(
            groups = {"unit"},
            description = "-Dgroups maps to a Cucumber tag expression")
    @Story("Group to tag mapping")
    @Severity(SeverityLevel.CRITICAL)
    public void shouldMapGroupsToTagExpression() {
        assertThat(CucumberTagFilter.toTagExpression("smoke, branches,"))
                .isEqualTo(
                        "(@smoke or @branches) and not @wip and not @blocked-env and not @out-of-scope and not @known-defect");
    }
}
