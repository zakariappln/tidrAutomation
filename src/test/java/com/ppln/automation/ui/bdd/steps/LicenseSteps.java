package com.ppln.automation.ui.bdd.steps;

import com.ppln.automation.core.utils.JsonUtils;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.pages.license.LicensePage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.assertj.core.api.Assertions;

/**
 * License page. The dashboard no longer renders the license text (its call is commented out in the
 * template), so "the dashboard license info" is read from the widget endpoint it used, over the
 * scenario's session.
 */
public class LicenseSteps {

    private final UiScenarioContext ctx;

    public LicenseSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("I am on the license page")
    public void iAmOnTheLicensePage() {
        new LicensePage(ctx.page()).open();
    }

    @Then("the dashboard license info should be {string}")
    public void theDashboardLicenseInfoShouldBe(String expected) {
        Assertions.assertThat(licenseInfo()).isEqualTo(ctx.resolve(expected));
    }

    @Then("the dashboard license info should not be {string}")
    public void theDashboardLicenseInfoShouldNotBe(String unexpected) {
        Assertions.assertThat(licenseInfo()).isNotEqualTo(ctx.resolve(unexpected));
    }

    private String licenseInfo() {
        var result = SessionHttp.send(ctx, "GET", LicensePage.LICENSE_INFO_PATH, null);
        Assertions.assertThat(result.status()).as("GET license-info status").isEqualTo(200);
        return JsonUtils.readTree(result.body()).path("data").asText();
    }
}
