package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.ppln.automation.core.dns.DnsClient;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.fixtures.AdminHttp.DomainList;
import com.ppln.automation.ui.pages.domains.BlockedDomainsPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.assertj.core.api.Assertions;

/**
 * Blocked Domains phrases. Mechanics (capture, cleanup, paging) live in {@link DomainLists};
 * phrases shared with Allowed Domains in {@link DomainSteps}; the list-agnostic bodies of the
 * per-list phrases in {@link DomainListSteps}.
 */
public class BlockedDomainsSteps {

    private static final DomainList LIST = DomainList.BLOCKED;

    private final UiScenarioContext ctx;
    private final DomainLists lists;
    private final DomainListSteps common;

    public BlockedDomainsSteps(UiScenarioContext ctx, DomainLists lists) {
        this.ctx = ctx;
        this.lists = lists;
        this.common = new DomainListSteps(ctx, lists, LIST);
    }

    private BlockedDomainsPage page() {
        return (BlockedDomainsPage) lists.page(LIST);
    }

    @Given("I am on the blocked domains page")
    public void iAmOnTheBlockedDomainsPage() {
        lists.open(LIST);
    }

    @When("I reload the blocked domains page")
    public void iReloadTheBlockedDomainsPage() {
        lists.open(LIST);
    }

    @When("I add the blocked domain {string}")
    public void iAddTheBlockedDomain(String domain) {
        lists.addThroughUi(LIST, ctx.resolve(domain));
    }

    @When("I add a blocked domain that is {int} characters long")
    public void iAddABlockedDomainThatIsCharactersLong(int length) {
        common.addDomainOfLength(length);
    }

    @When("I type a {int}-character value into the blocked domain field")
    public void iTypeACharacterValueIntoTheBlockedDomainField(int length) {
        common.typeValueOfLength(length);
    }

    @Then("the blocked domain field should hold at most {int} characters")
    public void theBlockedDomainFieldShouldHoldAtMostCharacters(int max) {
        common.fieldHoldsAtMost(max);
    }

    @When("I delete the blocked domain {string}")
    public void iDeleteTheBlockedDomain(String domain) {
        lists.deleteThroughUi(LIST, ctx.resolve(domain));
    }

    /** Precondition created as admin over HTTP (Inactive, so the RPZ zone is untouched). */
    @Given("{string} is already a blocked domain")
    public void isAlreadyABlockedDomain(String domain) {
        lists.createOverHttp(LIST, ctx.resolve(domain));
    }

    @Given("more than {int} blocked domains exist")
    public void moreThanBlockedDomainsExist(int count) {
        common.createMoreThan(count);
    }

    /** Sent over the current (possibly limited) session, because the UI may hide the button. */
    @When("I send a request to delete the blocked domain {string}")
    public void iSendARequestToDeleteTheBlockedDomain(String domain) {
        int id = lists.idOf(LIST, ctx.resolve(domain));
        SessionHttp.send(ctx, "DELETE", "/en/blocked_domains/delete/" + id, null);
    }

    @Then("the delete request should be answered with HTTP status {int}")
    public void theDeleteRequestShouldBeAnsweredWithHttpStatus(int status) {
        var result = ctx.lastHttp();
        Assertions.assertThat(result.status())
                .as("%s %s answered %s", result.method(), result.url(), result.body())
                .isEqualTo(status);
    }

    @Then("the blocked domain {string} should have no delete control")
    public void theBlockedDomainShouldHaveNoDeleteControl(String domain) {
        Locator row = page().rowFor(ctx.resolve(domain));
        assertThat(row).isVisible();
        assertThat(page().deleteButtonIn(row)).hasCount(0);
    }

    @Then("{string} should appear in the blocked domains list")
    public void shouldAppearInTheBlockedDomainsList(String domain) {
        assertThat(page().rowFor(ctx.resolve(domain))).isVisible();
    }

    @Then("{string} should no longer appear in the blocked domains list")
    public void shouldNoLongerAppearInTheBlockedDomainsList(String domain) {
        assertThat(page().rowFor(ctx.resolve(domain))).hasCount(0);
    }

    @Then("the blocked domains list should show {int} rows")
    public void theBlockedDomainsListShouldShowRows(int rows) {
        assertThat(page().table().rows()).hasCount(rows);
    }

    @Then("the blocked domains list should be ordered by {string} newest first")
    public void theBlockedDomainsListShouldBeOrderedByNewestFirst(String column) {
        DomainSteps.assertNewestFirst(page(), ctx.resolve(column));
    }

    @When("I go to page {int} of the blocked domains list")
    public void iGoToPageOfTheBlockedDomainsList(int number) {
        page().goToPage(number);
    }

    @Then("the blocked domains pager should show page {int} as current")
    public void theBlockedDomainsPagerShouldShowPageAsCurrent(int number) {
        assertThat(page().table().currentPage()).hasText(Integer.toString(number));
    }

    @Then("a DNS lookup for {string} should return NXDOMAIN")
    public void aDnsLookupForShouldReturnNxDomain(String domain) {
        String resolved = ctx.resolve(domain);
        var result = new DnsClient().resolveA(resolved);
        Assertions.assertThat(result.isNxDomain())
                .as(
                        "Expected NXDOMAIN for %s (RPZ block), got rcode=%d, addresses=%s",
                        resolved, result.rcode(), result.addresses())
                .isTrue();
    }
}
