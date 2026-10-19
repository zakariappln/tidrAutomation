package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.ppln.automation.core.dns.DnsClient;
import com.ppln.automation.core.dns.DnsClient.CnameResult;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.pages.domains.DomainListPage;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.assertj.core.api.Assertions;
import org.xbill.DNS.Rcode;

/**
 * Steps shared by Blocked and Allowed Domains (same table, same form): "its status", the add form's
 * error, list order and the RPZ zone checks. Phrases that name a list live in {@link
 * BlockedDomainsSteps} / {@link AllowedDomainsSteps} and delegate to {@link DomainLists} and the
 * helpers here.
 */
public class DomainSteps {

    private static final DateTimeFormatter CREATION_DATE =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.ENGLISH);

    /** Apply Changes reloads the zone before its redirect; this covers a click not yet finished. */
    private static final double ZONE_WAIT_MS = 15_000;

    private static final long QUERY_INTERVAL_MS = 250;

    private final UiScenarioContext ctx;
    private final DomainLists lists;

    public DomainSteps(UiScenarioContext ctx, DomainLists lists) {
        this.ctx = ctx;
        this.lists = lists;
    }

    /** Lets domain cleanups re-apply the RPZ zone, which is only safe under the settings lock. */
    @Before("@serial-settings")
    public void allowApplyInCleanup() {
        lists.inSerialSettingsScenario();
    }

    @Then("its status should be {string}")
    public void itsStatusShouldBe(String status) {
        DomainListPage page = lists.page(lists.currentList());
        assertThat(page.rowFor(ctx.get(DomainLists.LAST_DOMAIN))).containsText(ctx.resolve(status));
    }

    @Then("I should see the domain error {string}")
    public void iShouldSeeTheDomainError(String text) {
        assertThat(lists.page(lists.currentList()).domainError()).hasText(ctx.resolve(text));
    }

    /** The add step already waited for the server's answer, so the error shown is final. */
    @Then("I should not see the domain error {string}")
    public void iShouldNotSeeTheDomainError(String text) {
        assertThat(lists.page(lists.currentList()).domainError()).not().hasText(ctx.resolve(text));
    }

    @Then("the RPZ zone {string} should map {string} to {string}")
    public void theRpzZoneShouldMapTo(String zone, String domain, String target) {
        String name = ctx.resolve(domain) + "." + ctx.resolve(zone);
        String expected = ctx.resolve(target);
        CnameResult result = awaitZone(name, r -> r.targets().equals(List.of(expected)));
        Assertions.assertThat(result.targets())
                .as("CNAME of %s (rcode %s)", name, Rcode.string(result.rcode()))
                .containsExactly(expected);
    }

    @Then("the RPZ zone {string} should have no rule for {string}")
    public void theRpzZoneShouldHaveNoRuleFor(String zone, String domain) {
        String name = ctx.resolve(domain) + "." + ctx.resolve(zone);
        CnameResult result = new DnsClient().queryCname(name);
        Assertions.assertThat(result.targets())
                .as("CNAME of %s (rcode %s)", name, Rcode.string(result.rcode()))
                .isEmpty();
    }

    /**
     * Queries until {@code done} holds or {@link #ZONE_WAIT_MS} passes (the zone is rewritten by
     * the Apply request a preceding click may still be running), at most every {@link
     * #QUERY_INTERVAL_MS}; returns the last answer for the caller to assert on.
     */
    private CnameResult awaitZone(String name, Predicate<CnameResult> done) {
        DnsClient dns = new DnsClient();
        CnameResult[] last = {dns.queryCname(name)};
        long[] lastQuery = {System.currentTimeMillis()};
        if (done.test(last[0])) {
            return last[0];
        }
        try {
            ctx.page()
                    .waitForCondition(
                            () -> {
                                if (System.currentTimeMillis() - lastQuery[0] < QUERY_INTERVAL_MS) {
                                    return false;
                                }
                                lastQuery[0] = System.currentTimeMillis();
                                try {
                                    last[0] = dns.queryCname(name);
                                } catch (RuntimeException e) {
                                    return false;
                                }
                                return done.test(last[0]);
                            },
                            new Page.WaitForConditionOptions().setTimeout(ZONE_WAIT_MS));
        } catch (PlaywrightException timedOut) {
            // fall through: the caller's assertion reports the last answer
        }
        return last[0];
    }

    /** Asserts the list's "Creation Date" (or another date column) never increases downwards. */
    static void assertNewestFirst(DomainListPage page, String column) {
        List<LocalDateTime> dates =
                page.table().columnValues(column).stream()
                        .map(v -> LocalDateTime.parse(v, CREATION_DATE))
                        .toList();
        Assertions.assertThat(dates).as("%s column, top to bottom", column).isNotEmpty();
        Assertions.assertThat(dates)
                .as("%s column, top to bottom", column)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }
}
