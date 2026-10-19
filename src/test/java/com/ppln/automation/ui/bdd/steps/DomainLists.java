package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.fixtures.AdminHttp.DomainList;
import com.ppln.automation.ui.pages.domains.AllowedDomainsPage;
import com.ppln.automation.ui.pages.domains.BlockedDomainsPage;
import com.ppln.automation.ui.pages.domains.DomainListPage;
import java.util.Optional;
import org.assertj.core.api.Assertions;

/**
 * Scenario-scoped (PicoContainer) helper behind the Blocked / Allowed Domains steps: adds and
 * deletes through the UI while capturing the server's answer, creates preconditions over the admin
 * session, and owns their cleanup.
 *
 * <p>Cleanup deletes by id over HTTP (never through the UI: a failed scenario may have left the
 * page anywhere). Apply Changes rewrites the RPZ zone from <em>every</em> row and activates every
 * Inactive row globally, so a cleanup re-applies only inside a {@code @serial-settings} scenario
 * ({@link #inSerialSettingsScenario()}, set by {@link DomainSteps}); elsewhere a deleted row leaves
 * the zone at the next serial scenario's Apply.
 */
public final class DomainLists {

    /** The domain the scenario added last ("its status ..."). */
    static final String LAST_DOMAIN = "lastDomain";

    private static final String CURRENT_LIST = "__domainList";

    private final UiScenarioContext ctx;
    private boolean serialSettings;

    public DomainLists(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    void inSerialSettingsScenario() {
        serialSettings = true;
    }

    // ---- pages ----

    /** Page object for {@code list} on the scenario's page (no navigation). */
    DomainListPage page(DomainList list) {
        return switch (list) {
            case BLOCKED -> new BlockedDomainsPage(ctx.page());
            case ALLOWED -> new AllowedDomainsPage(ctx.page());
        };
    }

    DomainListPage open(DomainList list) {
        ctx.put(CURRENT_LIST, list.name());
        return page(list).openListPage(1);
    }

    /** The list page the scenario opened last. */
    DomainList currentList() {
        return DomainList.valueOf(ctx.get(CURRENT_LIST));
    }

    // ---- through the UI ----

    /**
     * Submits the add form and waits for the server's answer; a created row is registered for
     * cleanup and, as the outcome check, must show up after the page's reload.
     */
    void addThroughUi(DomainList list, String domain) {
        ctx.put(CURRENT_LIST, list.name());
        ctx.put(LAST_DOMAIN, domain);
        DomainListPage page = page(list);
        HttpResult answer;
        try (CapturedCall call = CapturedCall.of(ctx, "POST", "**/" + segment(list) + "/add")) {
            page.addDomain(domain);
            answer = call.await();
        }
        Assertions.assertThat(answer.status())
                .as("POST %s: %s", answer.url(), answer.body())
                .isEqualTo(200);
        Optional<Integer> id = list.idInAddResponse(answer.body());
        if (id.isPresent()) {
            track(list, domain, id.get());
            assertThat(page.rowFor(domain)).isVisible();
        } else {
            // a refused add: the server's error is rendered under the field
            assertThat(page.domainError()).isVisible();
        }
    }

    void deleteThroughUi(DomainList list, String domain) {
        ctx.put(CURRENT_LIST, list.name());
        DomainListPage page = page(list).openPageListing(domain);
        HttpResult answer;
        try (CapturedCall call =
                CapturedCall.of(ctx, "DELETE", "**/" + segment(list) + "/delete/*")) {
            page.deleteDomain(domain);
            answer = call.await();
        }
        Assertions.assertThat(answer.status())
                .as("DELETE %s: %s", answer.url(), answer.body())
                .isEqualTo(200);
        Assertions.assertThat(CapturedCall.json(answer).path("status").asText())
                .as("DELETE %s: %s", answer.url(), answer.body())
                .isEqualTo("Ok");
        markDeleted(list, domain);
    }

    // ---- over the admin session ----

    /** Creates the domain (Inactive) as admin over HTTP and registers its cleanup. */
    int createOverHttp(DomainList list, String domain) {
        int id = ctx.admin().createDomain(list, domain);
        track(list, domain, id);
        return id;
    }

    /** Deletes a domain this scenario created, as admin over HTTP; asserts the server agreed. */
    void deleteOverHttp(DomainList list, String domain) {
        var response = ctx.admin().deleteDomain(list, idOf(list, domain));
        Assertions.assertThat(response.status())
                .as("delete %s: %s", domain, response.text())
                .isEqualTo(200);
        markDeleted(list, domain);
    }

    int idOf(DomainList list, String domain) {
        return Integer.parseInt(ctx.get(idKey(list, domain)));
    }

    // ---- cleanup bookkeeping ----

    private void track(DomainList list, String domain, int id) {
        ctx.put(idKey(list, domain), Integer.toString(id));
        ctx.onCleanup(
                () -> {
                    if (!ctx.has(deletedKey(list, domain))) {
                        ctx.admin().deleteDomain(list, id);
                    }
                    if (serialSettings) {
                        ctx.admin().applyDomains(list);
                    }
                });
    }

    private void markDeleted(DomainList list, String domain) {
        ctx.put(deletedKey(list, domain), "true");
    }

    /**
     * Key under which batch-1 steps ("I send a request to delete the blocked domain") read the id.
     */
    static String idKey(DomainList list, String domain) {
        return list == DomainList.BLOCKED
                ? "__blockedDomainId:" + domain
                : "__allowedDomainId:" + domain;
    }

    private static String deletedKey(DomainList list, String domain) {
        return "__deleted:" + list + ":" + domain;
    }

    private static String segment(DomainList list) {
        return list == DomainList.BLOCKED ? "blocked_domains" : "allowed_domains";
    }
}
