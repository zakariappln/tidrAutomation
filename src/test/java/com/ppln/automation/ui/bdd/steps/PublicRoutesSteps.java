package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.pages.publicpages.BlockPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.assertj.core.api.Assertions;

/** The public block page ({@code /<lang>/nxdomain}) bind redirects blocked users to. */
public class PublicRoutesSteps {

    /** Set by the scenario's {@code <script>} host parameter if it ever runs. */
    private static final String XSS_MARKER = "__qa";

    private final UiScenarioContext ctx;

    public PublicRoutesSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Read as admin over HTTP from the Threat Detection table's data call (newest first), never by
     * opening the Threats page, which marks every threat viewed. Stores {@code
     * {newestThreatDomain}} and {@code {newestThreatType}}.
     */
    @Given("I have noted the destination domain and threat type of the newest threat")
    public void iHaveNotedTheDestinationDomainAndThreatTypeOfTheNewestThreat() {
        JsonNode threat =
                ctx.admin()
                        .newestThreat()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No threat in the last 30 days: the block page has"
                                                        + " nothing to name"));
        ctx.put("newestThreatDomain", threat.path("query_domain").asText());
        ctx.put("newestThreatType", threat.path("threat_type").asText());
    }

    /** The host was echoed as text (so it reached the page) and its script never ran. */
    @Then("no script from the host parameter should have executed")
    public void noScriptFromTheHostParameterShouldHaveExecuted() {
        BlockPage page = new BlockPage(ctx.page());
        assertThat(page.domainNameButton()).containsText("<script>");
        Object marker = ctx.page().evaluate("name => window[name] === undefined", XSS_MARKER);
        Assertions.assertThat(marker)
                .as("window.%s is still undefined", XSS_MARKER)
                .isEqualTo(true);
    }

    @Then("the block page should show the threat type {string}")
    public void theBlockPageShouldShowTheThreatType(String type) {
        assertThat(new BlockPage(ctx.page()).heading())
                .hasText(BlockPage.HEADING_PREFIX + ctx.resolve(type));
    }

    /** With no matching threat the template falls back to its generic "... From Malware". */
    @Then("the block page should show no threat type")
    public void theBlockPageShouldShowNoThreatType() {
        assertThat(new BlockPage(ctx.page()).heading())
                .hasText(BlockPage.HEADING_PREFIX + "Malware");
    }
}
