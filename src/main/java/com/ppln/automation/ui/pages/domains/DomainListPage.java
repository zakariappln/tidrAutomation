package com.ppln.automation.ui.pages.domains;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.ppln.automation.ui.pages.components.DomainTableComponent;

/**
 * What Blocked Domains and Allowed Domains have in common (same template structure, same JS), so
 * step code for list, pager, form errors and delete is written once.
 */
public sealed interface DomainListPage permits BlockedDomainsPage, AllowedDomainsPage {

    Page page();

    DomainTableComponent table();

    /** Opens list page {@code number} directly ({@code ?page=n}). */
    DomainListPage openListPage(int number);

    /** Clicks page {@code number} in the pager. */
    DomainListPage goToPage(int number);

    /** Opens the add form and submits {@code domain}; the page reloads on success. */
    DomainListPage addDomain(String domain);

    /** Opens the add form and types {@code value} key by key (as a user would). */
    DomainListPage typeDomain(String value);

    /** Clicks the row's Delete and confirms "Yes, Delete it"; the row is removed in place. */
    DomainListPage deleteDomain(String domain);

    Locator domainInput();

    /** The add form's error under the Domain field ({@code #error_add_<list>_domain}). */
    Locator domainError();

    default Locator rowFor(String domain) {
        return table().rowFor(domain);
    }

    /**
     * Opens the list page holding {@code domain} (newest first, so usually page 1, but rows added
     * by other scenarios meanwhile push it further); stays on the last page if it is on none.
     */
    default DomainListPage openPageListing(String domain) {
        openListPage(1);
        int last = table().lastPageNumber();
        for (int n = 2; n <= last && rowFor(domain).count() == 0; n++) {
            openListPage(n);
        }
        return this;
    }
}
