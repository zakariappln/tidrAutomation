package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.fixtures.AdminHttp.DomainList;
import com.ppln.automation.ui.pages.audit.AccessLogPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;

/** Audit trail preconditions (Blocked/Allowed history) and the Access log page. */
public class AuditSteps {

    private static final Pattern ACCESS_LOG_URL = Pattern.compile(".*/access_log/?(\\?.*)?$");

    private final UiScenarioContext ctx;
    private final DomainLists lists;

    public AuditSteps(UiScenarioContext ctx, DomainLists lists) {
        this.ctx = ctx;
        this.lists = lists;
    }

    /** Both requests go through the admin session; each writes one history row. */
    @Given("I add and then delete the blocked domain {string}")
    public void iAddAndThenDeleteTheBlockedDomain(String domain) {
        addAndDelete(DomainList.BLOCKED, ctx.resolve(domain));
    }

    @Given("I add and then delete the allowed domain {string}")
    public void iAddAndThenDeleteTheAllowedDomain(String domain) {
        addAndDelete(DomainList.ALLOWED, ctx.resolve(domain));
    }

    private void addAndDelete(DomainList list, String domain) {
        lists.createOverHttp(list, domain);
        lists.deleteOverHttp(list, domain);
    }

    // ---- access log ----

    @Then("the access log page should be shown, not a redirect to the dashboard")
    public void theAccessLogPageShouldBeShownNotARedirectToTheDashboard() {
        assertOnAccessLog();
    }

    private void assertOnAccessLog() {
        assertThat(ctx.page()).hasURL(ACCESS_LOG_URL);
        assertThat(new AccessLogPage(ctx.page()).table()).isVisible();
    }

    @Then("the table should group rows by client IP with hit counts")
    public void theTableShouldGroupRowsByClientIpWithHitCounts() {
        AccessLogPage page = new AccessLogPage(ctx.page());
        assertThat(page.columnHeader("IP")).isVisible();
        assertThat(page.columnHeader("Count")).isVisible();
        List<String> ips = page.columnValues("IP");
        Assertions.assertThat(ips).as("one row per client IP").doesNotHaveDuplicates();
        Assertions.assertThat(page.columnValues("Count"))
                .as("hit counts")
                .allMatch(c -> c.matches("\\d+") && Integer.parseInt(c) > 0);
    }

    @When("I filter the access log by the last {int} days and the client IP {string}")
    public void iFilterTheAccessLogByTheLastDaysAndTheClientIp(int days, String ip) {
        assertOnAccessLog(); // a redirect elsewhere would otherwise surface as a missing field
        new AccessLogPage(ctx.page()).filter("Last %d Days".formatted(days), ctx.resolve(ip));
    }

    @Then("every row shown should have client IP {string}")
    public void everyRowShownShouldHaveClientIp(String ip) {
        AccessLogPage page = new AccessLogPage(ctx.page());
        assertThat(page.table()).isVisible();
        Assertions.assertThat(page.columnValues("IP")).allMatch(ctx.resolve(ip)::equals);
    }
}
