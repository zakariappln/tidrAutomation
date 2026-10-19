package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.fixtures.AdminHttp.DomainList;
import com.ppln.automation.ui.pages.domains.AllowedDomainsPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Allowed Domains phrases. Mechanics (capture, cleanup, paging) live in {@link DomainLists};
 * phrases shared with Blocked Domains in {@link DomainSteps}; the list-agnostic bodies of the
 * per-list phrases in {@link DomainListSteps}.
 */
public class AllowedDomainsSteps {

    private static final DomainList LIST = DomainList.ALLOWED;

    private final UiScenarioContext ctx;
    private final DomainLists lists;
    private final DomainListSteps common;

    public AllowedDomainsSteps(UiScenarioContext ctx, DomainLists lists) {
        this.ctx = ctx;
        this.lists = lists;
        this.common = new DomainListSteps(ctx, lists, LIST);
    }

    private AllowedDomainsPage page() {
        return (AllowedDomainsPage) lists.page(LIST);
    }

    @Given("I am on the allowed domains page")
    public void iAmOnTheAllowedDomainsPage() {
        lists.open(LIST);
    }

    @When("I reload the allowed domains page")
    public void iReloadTheAllowedDomainsPage() {
        lists.open(LIST);
    }

    @When("I add the allowed domain {string}")
    public void iAddTheAllowedDomain(String domain) {
        lists.addThroughUi(LIST, ctx.resolve(domain));
    }

    @When("I add an allowed domain that is {int} characters long")
    public void iAddAnAllowedDomainThatIsCharactersLong(int length) {
        common.addDomainOfLength(length);
    }

    @When("I type a {int}-character value into the allowed domain field")
    public void iTypeACharacterValueIntoTheAllowedDomainField(int length) {
        common.typeValueOfLength(length);
    }

    @Then("the allowed domain field should hold at most {int} characters")
    public void theAllowedDomainFieldShouldHoldAtMostCharacters(int max) {
        common.fieldHoldsAtMost(max);
    }

    @When("I delete the allowed domain {string}")
    public void iDeleteTheAllowedDomain(String domain) {
        lists.deleteThroughUi(LIST, ctx.resolve(domain));
    }

    /** Precondition created as admin over HTTP (Inactive, so the RPZ zone is untouched). */
    @Given("{string} is already an allowed domain")
    public void isAlreadyAnAllowedDomain(String domain) {
        lists.createOverHttp(LIST, ctx.resolve(domain));
    }

    @Given("more than {int} allowed domains exist")
    public void moreThanAllowedDomainsExist(int count) {
        common.createMoreThan(count);
    }

    @Then("{string} should appear in the allowed domains list")
    public void shouldAppearInTheAllowedDomainsList(String domain) {
        assertThat(page().rowFor(ctx.resolve(domain))).isVisible();
    }

    @Then("{string} should no longer appear in the allowed domains list")
    public void shouldNoLongerAppearInTheAllowedDomainsList(String domain) {
        assertThat(page().rowFor(ctx.resolve(domain))).hasCount(0);
    }

    @Then("the allowed domains list should show {int} rows")
    public void theAllowedDomainsListShouldShowRows(int rows) {
        assertThat(page().table().rows()).hasCount(rows);
    }

    @Then("the allowed domains list should be ordered by {string} newest first")
    public void theAllowedDomainsListShouldBeOrderedByNewestFirst(String column) {
        DomainSteps.assertNewestFirst(page(), ctx.resolve(column));
    }

    @When("I go to page {int} of the allowed domains list")
    public void iGoToPageOfTheAllowedDomainsList(int number) {
        page().goToPage(number);
    }

    @Then("the allowed domains pager should show page {int} as current")
    public void theAllowedDomainsPagerShouldShowPageAsCurrent(int number) {
        assertThat(page().table().currentPage()).hasText(Integer.toString(number));
    }
}
