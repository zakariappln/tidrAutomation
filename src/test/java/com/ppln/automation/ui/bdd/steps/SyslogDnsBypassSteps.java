package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ppln.automation.core.dns.DnsClient;
import com.ppln.automation.core.utils.JsonUtils;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.pages.components.MessagesComponent;
import com.ppln.automation.ui.pages.settings.SettingsPage;
import com.ppln.automation.ui.pages.settings.SyslogTab;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;

/**
 * Settings &gt; System Log (syslog forwarding target) and the DNS bypass switch
 * (views/setting/views.py:604-660, 1217-1272).
 *
 * <p>SAFETY. Once the Settings page has been rendered a syslog row always exists, so any syslog
 * save that passes the server's connection test updates it and broadcasts a Celery worker shutdown
 * (views.py:1256-1259). "I save the syslog target ..." therefore lets the request through only when
 * the server must reject it: an invalid IP, a port outside 0-65535, or a TCP test to 127.0.0.1:9
 * (nothing listens there in the app container). Anything else is aborted before it is sent. For the
 * same reason the original syslog values are verified, not re-saved, in cleanup.
 */
public class SyslogDnsBypassSteps {

    private static final Pattern IPV4 =
            Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
    private static final String CLOSED_TCP_HOST = "127.0.0.1";
    private static final int CLOSED_TCP_PORT = 9;

    /** Stored syslog target as the page renders it, and the DNS bypass state. */
    private record Stored(
            String ip, String port, boolean forwarding, boolean tcp, boolean bypass) {}

    private final UiScenarioContext ctx;

    public SyslogDnsBypassSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("the original syslog and DNS bypass settings are recorded for restore")
    public void theOriginalSyslogAndDnsBypassSettingsAreRecordedForRestore() {
        Stored original = stored();
        ctx.onCleanup(
                () -> {
                    if (stored().bypass() != original.bypass()) {
                        ctx.admin().setDnsBypass(original.bypass());
                    }
                    Stored now = stored();
                    if (!now.equals(original)) {
                        throw new IllegalStateException(
                                "Syslog/DNS bypass settings changed: now %s, originally %s (syslog"
                                        + " is not re-saved automatically: a save restarts Celery)"
                                                .formatted(now, original));
                    }
                });
    }

    @When("I save the syslog target {string} port {string} over {string}")
    public void iSaveTheSyslogTargetPortOver(String ip, String port, String protocol) {
        String transport = ctx.resolve(protocol);
        Assertions.assertThat(transport).as("protocol").isIn("TCP", "UDP");
        SyslogTab tab = new SettingsPage(ctx.page()).syslog();
        tab.fill(ctx.resolve(ip), ctx.resolve(port), "TCP".equals(transport));
        CapturedCall call =
                CapturedCall.guarded(
                        ctx,
                        "POST",
                        "**/settings/syslog-settings",
                        SyslogDnsBypassSteps::refuseIfSaveCouldSucceed);
        tab.saveButton().click();
        ctx.expectPendingCall(call::await);
    }

    /** The server's error ({@code [{"errors": ...}, 400]}, HTTP 409) and the toast showing it. */
    @Then("I should see the syslog message {string}")
    public void iShouldSeeTheSyslogMessage(String text) {
        ctx.awaitPendingCalls();
        String expected = ctx.resolve(text);
        HttpResult result = ctx.lastHttp();
        Assertions.assertThat(CapturedCall.json(result).path(0).path("errors").asText())
                .as("syslog-settings answer HTTP %d %s", result.status(), result.body())
                .isEqualTo(expected);
        assertThat(new MessagesComponent(ctx.page()).withText(expected).first()).isVisible();
    }

    @Then("reloading the settings page should not show {string} port {string} as the syslog target")
    public void reloadingTheSettingsPageShouldNotShowPortAsTheSyslogTarget(String ip, String port) {
        ctx.awaitPendingCalls();
        SettingsPage settings = new SettingsPage(ctx.page()).open();
        settings.selectTab("System Log");
        SyslogTab tab = settings.syslog();
        assertThat(tab.serverIp()).isVisible();
        String shown = tab.serverIp().inputValue() + ":" + tab.serverPort().inputValue();
        Assertions.assertThat(shown)
                .as("stored syslog target")
                .isNotEqualTo(ctx.resolve(ip) + ":" + ctx.resolve(port));
    }

    /** The "Detect Dns Bypass" tab is commented out, so its request is sent directly. */
    @When("I enable DNS Bypass")
    public void iEnableDnsBypass() {
        setBypass("Enable");
    }

    @When("I disable DNS Bypass")
    public void iDisableDnsBypass() {
        setBypass("Disable");
    }

    @Then("a DNS lookup for {string} should not return NXDOMAIN")
    public void aDnsLookupForShouldNotReturnNxDomain(String domain) {
        String resolved = ctx.resolve(domain);
        var result = new DnsClient().resolveA(resolved);
        Assertions.assertThat(result.isNxDomain())
                .as("NXDOMAIN for %s (rcode %d)", resolved, result.rcode())
                .isFalse();
    }

    // ---- helpers ----

    private void setBypass(String status) {
        HttpResult result =
                SessionHttp.send(
                        ctx,
                        "POST",
                        "/en/settings/dns-bypass",
                        SessionHttp.withCsrf(ctx.page())
                                .setHeader("Content-Type", "application/json")
                                .setData(Map.of("status", status)));
        Assertions.assertThat(CapturedCall.json(result).path("status").asText())
                .as("dns-bypass answer HTTP %d %s", result.status(), result.body())
                .isEqualTo("Ok");
    }

    private Stored stored() {
        String html = ctx.admin().settingsHtml();
        return new Stored(
                attribute(html, "syslogServerIP", "value"),
                attribute(html, "syslogServerPort", "value"),
                checked(html, "forwardSyslog"),
                checked(html, "socktype"),
                checked(html, "detect-dns-switch"));
    }

    /** First element with this id (the template keeps a commented-out copy of the form below). */
    private static String attribute(String html, String id, String name) {
        Matcher m =
                Pattern.compile("id=\"" + id + "\"[^>]*?" + name + "=\"([^\"]*)\"").matcher(html);
        if (!m.find()) {
            throw new IllegalStateException("No #" + id + " on the settings page");
        }
        return m.group(1);
    }

    private static boolean checked(String html, String id) {
        Matcher m = Pattern.compile("id=\"" + id + "\"\\s*([^/>]*)").matcher(html);
        if (!m.find()) {
            throw new IllegalStateException("No #" + id + " on the settings page");
        }
        return m.group(1).contains("checked");
    }

    /**
     * Reason to abort a syslog save, or empty if the server is certain to reject it before storing
     * (see class comment). Port 0 or a missing port becomes 514 on the server.
     */
    static Optional<String> refuseIfSaveCouldSucceed(String body) {
        JsonNode json;
        try {
            json = JsonUtils.readTree(body);
        } catch (RuntimeException e) {
            return Optional.empty(); // the server cannot parse it either
        }
        String ip = json.path("ip").asText("");
        long port = json.path("port").asLong(0);
        boolean tcp = json.path("socktype").asBoolean(false);
        if (!mayBeValidIp(ip) || port < 0 || port > 65535) {
            return Optional.empty();
        }
        if (tcp && CLOSED_TCP_HOST.equals(ip) && port == CLOSED_TCP_PORT) {
            return Optional.empty();
        }
        return Optional.of(
                "syslog target %s:%d (TCP=%s) could pass the connection test, be stored and shut"
                        + " Celery down".formatted(ip, port, tcp));
    }

    private static boolean mayBeValidIp(String ip) {
        if (ip.contains(":")) {
            return true; // IPv6: treat as valid
        }
        Matcher m = IPV4.matcher(ip);
        if (!m.matches()) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            if (Integer.parseInt(m.group(i)) > 255) {
                return false;
            }
        }
        return true;
    }
}
