package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.pages.modules.DgaDomainsPage;
import com.ppln.automation.ui.pages.modules.HistoryOfIpPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;

/**
 * Modules without a menu entry: DGA Domains and History of IP (Site Risk Indicator only uses shared
 * phrases). On this build their views render templates that are not shipped, so every page is an
 * HTTP 500; these steps are written against the shipped {@code _dga_domain/} and {@code
 * _history_of_ip/} templates and only run after the page-load check, in the {@code @known-defect}
 * scenarios.
 */
public class HiddenModulesSteps {

    private static final Pattern IP_ADDRESS =
            Pattern.compile("^(\\d{1,3}(\\.\\d{1,3}){3}|[0-9a-fA-F:]+:[0-9a-fA-F:.]*)$");

    private final UiScenarioContext ctx;

    public HiddenModulesSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Then("I should see the DGA-predicted domains list")
    public void iShouldSeeTheDgaPredictedDomainsList() {
        DgaDomainsPage page = new DgaDomainsPage(ctx.page());
        assertThat(page.heading()).isVisible();
        assertThat(page.domainsColumnHeader()).isVisible();
    }

    /**
     * Submits the page's lookup form ({@code POST /en/history_of_ip/}, field {@code ip_address}).
     * The page that holds that form cannot be loaded on this build, so the same form is built and
     * submitted from the dashboard (same session and CSRF token): a real browser POST navigation.
     */
    @When("I look up {string} on the history of IP page")
    public void iLookUpOnTheHistoryOfIpPage(String value) {
        ctx.page().navigate("/en/dashboard/");
        String token = SessionHttp.csrfToken(ctx.page());
        ctx.page()
                .evaluate(
                        """
                        ([action, token, value]) => {
                          const form = document.createElement('form');
                          form.method = 'POST';
                          form.action = action;
                          for (const [name, v] of [['csrf_token', token], ['ip_address', value]]) {
                            const input = document.createElement('input');
                            input.type = 'hidden';
                            input.name = name;
                            input.value = v;
                            form.appendChild(input);
                          }
                          document.body.appendChild(form);
                          form.submit();
                        }""",
                        List.of("/en/history_of_ip/", token, ctx.resolve(value)));
        ctx.page().waitForURL("**/history_of_ip/");
    }

    @Then("history for the single highest-hit source IP should be shown")
    public void historyForTheSingleHighestHitSourceIpShouldBeShown() {
        HistoryOfIpPage page = new HistoryOfIpPage(ctx.page());
        assertThat(page.lookupField()).hasValue(IP_ADDRESS);
        String ip = page.lookupField().inputValue();
        assertRowsScopedTo(page, ip);
    }

    @Then("the history table should be scoped to {string}")
    public void theHistoryTableShouldBeScopedTo(String value) {
        String resolved = ctx.resolve(value);
        HistoryOfIpPage page = new HistoryOfIpPage(ctx.page());
        assertThat(page.lookupField()).hasValue(resolved);
        assertRowsScopedTo(page, resolved);
    }

    private static void assertRowsScopedTo(HistoryOfIpPage page, String value) {
        boolean isIp = IP_ADDRESS.matcher(value).matches();
        List<String> cells =
                (isIp ? page.sourceIpCells() : page.domainCells())
                        .allInnerTexts().stream().map(String::trim).toList();
        Assertions.assertThat(cells)
                .as("%s column of the history table", isIp ? "Source IP" : "Domains")
                .allMatch(value::equals);
    }
}
