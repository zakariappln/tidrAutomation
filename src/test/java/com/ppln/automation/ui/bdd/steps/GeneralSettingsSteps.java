package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIResponse;
import com.ppln.automation.core.dns.DnsClient;
import com.ppln.automation.core.utils.JsonUtils;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.pages.components.MessagesComponent;
import com.ppln.automation.ui.pages.settings.GeneralSettingsTab;
import com.ppln.automation.ui.pages.settings.SettingsPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Rcode;

/**
 * Settings &gt; General Settings: default language/timezone, RPZ category actions, Access Control.
 *
 * <p>SAFETY. Every RPZ save and every Access Control save that passes validation regenerates
 * bind9's named.conf ({@code core/utils.py write_named_conf}) with {@code allow-query { localhost;
 * <networks>; }} and runs {@code rndc reconfig}. The deployment's hand-written {@code allow-query {
 * any; }} cannot be produced by the app, so such a save would lock the test host (which reaches
 * bind through the docker port, i.e. from the gateway address) out of DNS and cannot be undone from
 * the UI. Therefore "I save Access Control" only lets the request through when it carries a value
 * the server rejects before writing (views/setting/views.py:475-493), and no RPZ save step exists:
 * those scenarios stay {@code @wip}. The background step also checks, in cleanup, that bind9 still
 * answers the test host.
 */
public class GeneralSettingsSteps {

    private static final Logger log = LoggerFactory.getLogger(GeneralSettingsSteps.class);
    private static final String ALLOW_DNS_QUERIES = "Allow DNS Queries";
    private static final List<String> RPZ_ACTIONS = List.of("Alert", "Block", "Off");
    private static final Pattern SELECTED_OPTION =
            Pattern.compile("<option selected value=\"([^\"]*)\">");

    /** Stored default language and timezone. */
    private record General(String language, String timezone) {}

    private final UiScenarioContext ctx;

    public GeneralSettingsSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Reads the stored language/timezone from the server-rendered form (the select's {@code
     * selected} option) and restores them over HTTP in cleanup, verifying the read-back. Also
     * asserts bind9 answers the test host now, and re-checks it in cleanup as a tripwire.
     */
    @Given("the original general settings are recorded for restore")
    public void theOriginalGeneralSettingsAreRecordedForRestore() {
        assertDnsAnswersTestHost();
        ctx.restoreAfterwards(
                this::storedGeneral,
                original -> {
                    APIResponse response =
                            ctx.admin()
                                    .saveGeneralSettings(original.language(), original.timezone());
                    General now = storedGeneral();
                    if (!now.equals(original)) {
                        throw new IllegalStateException(
                                "General settings not restored: HTTP %d %s, now %s, expected %s"
                                        .formatted(
                                                response.status(), response.text(), now, original));
                    }
                });
        ctx.onCleanup(
                () -> {
                    if (!dnsAnswersTestHost()) {
                        log.error(
                                "bind9 no longer answers the test host: named.conf was rewritten"
                                        + " (allow-query). Restore config/bind9/named.conf and"
                                        + " reload bind9.");
                    }
                });
    }

    @When("I set the default language to {string} and timezone to {string}")
    public void iSetTheDefaultLanguageToAndTimezoneTo(String language, String timezone) {
        tab().chooseLanguageAndTimezone(ctx.resolve(language), ctx.resolve(timezone));
        // the save is triggered by the generic "I click "Save""; outcome steps wait for it
        CapturedCall call = CapturedCall.of(ctx, "POST", "**/settings/general-settings");
        ctx.expectPendingCall(
                () -> {
                    var result = call.await();
                    Assertions.assertThat(result.status()).as(result.body()).isEqualTo(200);
                    Assertions.assertThat(CapturedCall.json(result).path("status").asText())
                            .as("general-settings answer %s", result.body())
                            .isEqualTo("Ok");
                });
    }

    @Then("reloading the settings page should show {string} and {string}")
    public void reloadingTheSettingsPageShouldShowAnd(String language, String timezone) {
        ctx.awaitPendingCalls();
        GeneralSettingsTab tab = reopen();
        assertThat(tab.defaultLanguage().locator("option:checked")).hasText(ctx.resolve(language));
        assertThat(tab.defaultTimezone()).hasValue(ctx.resolve(timezone));
    }

    @Then("the default language options should be exactly {string} and {string}")
    public void theDefaultLanguageOptionsShouldBeExactlyAnd(String first, String second) {
        assertThat(tab().defaultLanguage().locator("option"))
                .hasText(new String[] {ctx.resolve(first), ctx.resolve(second)});
    }

    /** The given action is the selected one and the two other actions are disabled options. */
    @Then("the {string} RPZ action should be {string} and not editable")
    public void theRpzActionShouldBeAndNotEditable(String category, String action) {
        GeneralSettingsTab tab = tab();
        String name = ctx.resolve(category);
        String expected = ctx.resolve(action);
        assertThat(tab.rpzSelectedOption(name)).hasText(expected);
        for (String other : RPZ_ACTIONS) {
            if (!other.equals(expected)) {
                assertThat(tab.rpzOption(name, other)).isDisabled();
            }
        }
    }

    @When("I add {string} to {string}")
    public void iAddTo(String value, String field) {
        requireAccessControl(field);
        String network = ctx.resolve(value);
        tab().addNetwork(network);
        assertThat(tab().networkTag(network)).hasCount(1);
    }

    @When("I remove every network from {string}")
    public void iRemoveEveryNetworkFrom(String field) {
        requireAccessControl(field);
        GeneralSettingsTab tab = tab();
        int count = tab.networkTags().count();
        for (int i = 0; i < count; i++) {
            tab.networkTags().first().locator(".tagify__tag__removeBtn").click();
        }
        assertThat(tab.networkTags()).hasCount(0);
    }

    /**
     * Clicks Access Control's Save. The request passes only if it cannot reach {@code
     * write_named_conf} (see class comment); otherwise it is aborted and the step fails.
     */
    @When("I save Access Control")
    public void iSaveAccessControl() {
        GeneralSettingsTab tab = tab();
        CapturedCall call =
                CapturedCall.guarded(
                        ctx,
                        "POST",
                        "**/settings/general-bind-settings-ip",
                        GeneralSettingsSteps::refuseIfNamedConfWouldBeWritten);
        tab.accessControlSaveButton().click();
        ctx.expectPendingCall(
                () ->
                        Assertions.assertThat(call.await(() -> tab.emptyNetworkField().count() > 0))
                                .as(
                                        "Access Control save request (none: the browser blocked"
                                                + " the submit because the hidden, required"
                                                + " network input is empty)")
                                .isPresent());
    }

    /** The error dialog Access Control shows ({@code res.errors}, trailing blank ignored). */
    @Then("I should see the access control error {string}")
    public void iShouldSeeTheAccessControlError(String text) {
        ctx.awaitPendingCalls();
        String expected = ctx.resolve(text).trim();
        Assertions.assertThat(CapturedCall.json(ctx.lastHttp()).path("errors").asText().trim())
                .as("general-bind-settings-ip answer")
                .isEqualTo(expected);
        assertThat(new MessagesComponent(ctx.page()).withText(expected).first()).isVisible();
    }

    // ---- helpers ----

    private GeneralSettingsTab tab() {
        return new SettingsPage(ctx.page()).general();
    }

    private GeneralSettingsTab reopen() {
        SettingsPage settings = new SettingsPage(ctx.page()).open();
        settings.selectTab("General Settings");
        return settings.general();
    }

    private General storedGeneral() {
        String html = ctx.admin().settingsHtml();
        return new General(
                selectedValue(html, "default_lang"), selectedValue(html, "default_timezone"));
    }

    private static String selectedValue(String html, String selectId) {
        int start = html.indexOf("id=\"" + selectId + "\"");
        int end = html.indexOf("</select>", start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("No select #" + selectId + " on the settings page");
        }
        Matcher m = SELECTED_OPTION.matcher(html.substring(start, end));
        if (!m.find()) {
            throw new IllegalStateException("No stored value selected in #" + selectId);
        }
        return m.group(1);
    }

    private static void requireAccessControl(String field) {
        Assertions.assertThat(field)
                .as("only the Access Control field is supported")
                .isEqualTo(ALLOW_DNS_QUERIES);
    }

    /**
     * Reason to abort an Access Control save, or empty if the server rejects it before writing
     * named.conf: an empty or unparseable list, or at least one value that validators 0.35 rejects
     * ({@code ipv4(v, host_bit=False)} = {@code IPv4Network(v, strict=True)}, {@code ipv6(v)}, not
     * "localhost"). The Java check errs towards "valid", so a doubtful value blocks the request.
     */
    static Optional<String> refuseIfNamedConfWouldBeWritten(String body) {
        List<String> networks = new ArrayList<>();
        try {
            String field = JsonUtils.readTree(body).path("dns_allowed_networks").asText("");
            if (field.isBlank()) {
                return Optional.empty();
            }
            JsonNode tags = JsonUtils.readTree(field);
            tags.forEach(t -> networks.add(t.path("value").asText().trim()));
        } catch (RuntimeException e) {
            return Optional.empty(); // the server's json.loads fails the same way
        }
        if (networks.isEmpty() || networks.stream().anyMatch(n -> !mayBeValidNetwork(n))) {
            return Optional.empty();
        }
        return Optional.of(
                "every Access Control value %s is valid, so the save would rewrite named.conf"
                        .formatted(networks));
    }

    private static final Pattern IPV4 =
            Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?:/(\\d{1,2}))?");

    private static boolean mayBeValidNetwork(String value) {
        if (value.equals("localhost") || value.contains(":")) {
            return true; // IPv6 or the localhost exception: treat as accepted
        }
        Matcher m = IPV4.matcher(value);
        if (!m.matches()) {
            return false;
        }
        long address = 0;
        for (int i = 1; i <= 4; i++) {
            int octet = Integer.parseInt(m.group(i));
            if (octet > 255) {
                return false;
            }
            address = (address << 8) | octet;
        }
        int prefix = m.group(5) == null ? 32 : Integer.parseInt(m.group(5));
        if (prefix > 32) {
            return false;
        }
        long hostMask = prefix == 32 ? 0 : (1L << (32 - prefix)) - 1;
        return (address & hostMask) == 0;
    }

    private void assertDnsAnswersTestHost() {
        Assertions.assertThat(dnsAnswersTestHost())
                .as("bind9 answers the test host before the scenario changes anything")
                .isTrue();
    }

    /** A query bind9 answers from its own RPZ zone; REFUSED means allow-query excludes us. */
    private static boolean dnsAnswersTestHost() {
        int rcode = new DnsClient().queryCname("pplnbl.host.ppln").rcode();
        return rcode != Rcode.REFUSED && rcode != Rcode.SERVFAIL;
    }
}
