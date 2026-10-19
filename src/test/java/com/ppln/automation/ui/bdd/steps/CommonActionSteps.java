package com.ppln.automation.ui.bdd.steps;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import io.cucumber.java.en.When;

/**
 * Generic "click the thing labelled X" step reused across many features ("Save", "View",
 * "Generate", "Apply Changes", ...). ThreatIDR's button-styled controls are inconsistently marked
 * up - real {@code <button>}s, {@code <a href="javascript:void(0)">} (role=link, a present if no-op
 * href is enough), and {@code <a type="submit">} with NO href at all, which gets NO implicit
 * accessible role per the ARIA spec (confirmed live 2026-10-17 on Blocked Domains' "Add Domain"
 * control - visible on screen, but getByRole(LINK) never matches it). This step tries button, then
 * link, then falls back to exact visible text via {@link Locator#or}, so feature authors and other
 * step classes don't need to know or encode that distinction per control.
 */
public class CommonActionSteps {

    private final UiScenarioContext ctx;

    public CommonActionSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("I click {string}")
    public void iClick(String label) {
        click(ctx.page(), ctx.resolve(label));
    }

    static void click(Page page, String resolvedLabel) {
        Locator button =
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(resolvedLabel));
        Locator link =
                page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(resolvedLabel));
        Locator text = page.getByText(resolvedLabel, new Page.GetByTextOptions().setExact(true));
        // pages such as Settings render several same-named controls in hidden tab panes ("Save"
        // exists six times); only the visible one is the one a user can click
        button.or(link)
                .or(text)
                .filter(new Locator.FilterOptions().setVisible(true))
                .first()
                .click();
    }
}
