package com.ppln.automation.ui.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.ppln.automation.core.config.ConfigManager;
import com.ppln.automation.core.config.FrameworkConfig;
import com.ppln.automation.core.dns.DnsClient;
import com.ppln.automation.core.reporting.AllureAttachments;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.driver.BrowserKind;
import com.ppln.automation.ui.fixtures.AdminHttp;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.ResolverConfig;

/**
 * End-to-end blocking with a real domain: does a client that resolves through ThreatIDR's bind9
 * still reach it? The test host's DNS is set to ThreatIDR's bind9 by hand (environment, not test
 * data), so the browser resolves through RPZ exactly like a protected client.
 *
 * <p>Every check launches a fresh browser: Chromium caches host lookups in-process, so a second
 * visit to the same name in one browser could answer from the pre-block (or pre-unblock) lookup.
 */
public class DomainAccessSteps {

    /**
     * A block or unblock is not enforced the instant Apply Changes returns: the zone reloads within
     * a second, but BIND applies a changed response-policy zone at most once per
     * min-update-interval (default 60 s; named.conf does not set it), so enforcement can lag up to
     * about a minute after a previous Apply (verified by hand 2026-10-24).
     */
    private static final double PROPAGATION_TIMEOUT_MS = 75_000;

    private static final double NAVIGATION_TIMEOUT_MS = 20_000;
    private static final String NXDOMAIN = Rcode.string(Rcode.NXDOMAIN);

    private final UiScenarioContext ctx;

    public DomainAccessSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Removes a leftover row for the domain (e.g. from an interrupted run) and applies, so the
     * scenario starts from "not blocked". Only for @serial-settings scenarios: Apply is global.
     */
    @Given("{string} is not a blocked domain")
    public void isNotABlockedDomain(String domain) {
        String name = ctx.resolve(domain);
        var leftover = ctx.admin().domainId(AdminHttp.DomainList.BLOCKED, name);
        if (leftover.isPresent()) {
            ctx.admin().deleteDomain(AdminHttp.DomainList.BLOCKED, leftover.get());
            ctx.admin().applyDomains(AdminHttp.DomainList.BLOCKED);
            AllureAttachments.text("Leftover removed", name + " (id " + leftover.get() + ")");
        }
    }

    @When("the blocked domain changes are applied")
    public void theBlockedDomainChangesAreApplied() {
        var response = ctx.admin().applyDomains(AdminHttp.DomainList.BLOCKED);
        assertThat(response.status()).as("Apply blocked domains over HTTP").isLessThan(500);
    }

    @Then("{string} should be accessible from the browser")
    public void shouldBeAccessibleFromTheBrowser(String name) {
        String host = ctx.resolve(name);
        awaitResolverAnswer(host, false);
        Outcome outcome = visit(host);
        assertThat(outcome.error())
                .as(
                        "https://%s/ should load (resolver %s answered %s)",
                        host, systemResolver(), outcome.rcode())
                .isNull();
    }

    @Then("{string} should not be accessible from the browser")
    public void shouldNotBeAccessibleFromTheBrowser(String name) {
        String host = ctx.resolve(name);
        awaitResolverAnswer(host, true);
        Outcome outcome = visit(host);
        assertThat(outcome.error())
                .as(
                        "https://%s/ should fail to resolve (resolver %s answered %s; it must be ThreatIDR's bind9)",
                        host, systemResolver(), outcome.rcode())
                .isNotNull()
                .contains("ERR_NAME_NOT_RESOLVED");
        assertThat(outcome.rcode()).as("system resolver answer for %s", host).isEqualTo(NXDOMAIN);
    }

    /**
     * Polls the host's resolver (bounded) until it gives the expected answer and records how long
     * that took; the browser visit that follows is the actual assertion. Querying through the
     * resolver cannot mask a block: RPZ rewrites answers after recursion, cached ones included.
     */
    private void awaitResolverAnswer(String host, boolean expectBlocked) {
        long started = System.nanoTime();
        boolean reached = true;
        try {
            ctx.page()
                    .waitForCondition(
                            () -> NXDOMAIN.equals(systemResolverRcode(host)) == expectBlocked,
                            new Page.WaitForConditionOptions().setTimeout(PROPAGATION_TIMEOUT_MS));
        } catch (PlaywrightException timedOut) {
            reached = false; // the browser visit below reports the real outcome
        }
        AllureAttachments.text(
                "DNS propagation " + host,
                "%s %s after %d ms"
                        .formatted(
                                expectBlocked ? "blocked" : "resolving",
                                reached ? "confirmed" : "NOT reached",
                                (System.nanoTime() - started) / 1_000_000));
    }

    private record Outcome(String error, String rcode) {}

    /** Any HTTP answer at all means the name resolved and the site was reached. */
    private static Outcome visit(String host) {
        String rcode = systemResolverRcode(host);
        FrameworkConfig cfg = ConfigManager.config();
        try (Playwright playwright = Playwright.create()) {
            Browser browser = BrowserKind.from(cfg.browser()).launch(playwright, cfg);
            try (BrowserContext context = browser.newContext()) {
                Page tab = context.newPage();
                Response response =
                        tab.navigate(
                                "https://" + host + "/",
                                new Page.NavigateOptions().setTimeout(NAVIGATION_TIMEOUT_MS));
                AllureAttachments.text(
                        "Visit " + host,
                        "HTTP "
                                + (response == null ? "-" : response.status())
                                + ", resolver "
                                + rcode);
                return new Outcome(null, rcode);
            } catch (PlaywrightException e) {
                // Playwright's message spans several lines; keep it whole so net::ERR_* codes
                // are matched wherever they appear.
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                AllureAttachments.text("Visit " + host, message + "\nresolver " + rcode);
                return new Outcome(message, rcode);
            } finally {
                browser.close();
            }
        }
    }

    private static InetSocketAddress systemResolver() {
        return ResolverConfig.getCurrentConfig().servers().getFirst();
    }

    /**
     * The host resolver's answer for the name, or {@code TIMEOUT} when it did not answer (UDP can
     * drop a packet). Never throws: a resolver hiccup is retried by the propagation poll and
     * reported in the assertion message instead of aborting the scenario.
     */
    private static String systemResolverRcode(String host) {
        InetSocketAddress system = systemResolver();
        DnsClient client =
                new DnsClient(
                        system.getAddress().getHostAddress(),
                        system.getPort(),
                        Duration.ofSeconds(5));
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return Rcode.string(client.resolveA(host).rcode());
            } catch (IllegalStateException noAnswer) {
                // try once more, then report
            }
        }
        return "TIMEOUT";
    }
}
