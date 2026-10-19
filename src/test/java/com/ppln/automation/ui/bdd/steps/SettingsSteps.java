package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.pages.settings.SettingsPage;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Settings page: tab navigation and the User Log (Blocked/Allowed history). */
public class SettingsSteps {

    private static final String USER_LOG = "User Log";
    private static final Pattern ACTIVE = Pattern.compile("\\bactive\\b");

    private final UiScenarioContext ctx;

    public SettingsSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("I am on the {string} settings tab")
    public void iAmOnTheSettingsTab(String label) {
        openTab(ctx.resolve(label));
    }

    /**
     * Each expected row is looked up by its (unique) domain, never by position: the log is shared
     * by every scenario and only shows 20 rows per page. Opens Settings > User Log afresh so rows
     * written by the preceding steps are included.
     */
    @Then("the user log should contain:")
    public void theUserLogShouldContain(DataTable expected) {
        List<Map<String, String>> rows = expected.asMaps();
        SettingsPage settings = openTab(USER_LOG);
        rows.stream()
                .map(r -> ctx.resolve(r.get("domain")))
                .distinct()
                .forEach(
                        domain -> {
                            settings.filterUserLog(domain);
                            rows.stream()
                                    .filter(r -> ctx.resolve(r.get("domain")).equals(domain))
                                    .forEach(
                                            r ->
                                                    assertThat(
                                                                    settings.userLogRow(
                                                                            domain,
                                                                            ctx.resolve(
                                                                                    r.get(
                                                                                            "event type")),
                                                                            ctx.resolve(
                                                                                    r.get(
                                                                                            "action"))))
                                                            .hasCount(1));
                        });
    }

    private SettingsPage openTab(String label) {
        SettingsPage settings = new SettingsPage(ctx.page()).open();
        settings.selectTab(label);
        assertThat(settings.tab(label)).hasClass(ACTIVE);
        return settings;
    }
}
