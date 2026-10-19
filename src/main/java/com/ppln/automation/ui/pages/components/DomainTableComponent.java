package com.ppln.automation.ui.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;

/**
 * The list block of Blocked / Allowed Domains ({@code #blocked-domain-list}, {@code
 * #whitelisted-domain-list}): a server-rendered table (Domain / Creation Date / Status / Action, 10
 * rows per page) and the pager under it, whose current page is the {@code a.page-link.active-page}.
 */
public final class DomainTableComponent extends BaseComponent {

    private final Locator rows;
    private final Locator headers;
    private final Locator pager;

    public DomainTableComponent(Page page, String listContainerId) {
        super(page, page.locator("#" + listContainerId));
        rows = root.locator("table tbody tr");
        headers = root.locator("table thead th");
        pager = root.locator("ul.pagination");
    }

    public Locator rows() {
        return rows;
    }

    /** The row whose Domain cell is exactly {@code domain}. */
    public Locator rowFor(String domain) {
        return rows.filter(
                new Locator.FilterOptions()
                        .setHas(
                                page.getByRole(
                                        AriaRole.CELL,
                                        new Page.GetByRoleOptions()
                                                .setName(domain)
                                                .setExact(true))));
    }

    /** Text of every cell in the column headed {@code header}, whitespace collapsed. */
    public List<String> columnValues(String header) {
        List<String> titles = headers.allInnerTexts().stream().map(String::strip).toList();
        int index = titles.indexOf(header);
        if (index < 0) {
            throw new IllegalArgumentException("No column '%s' in %s".formatted(header, titles));
        }
        return rows.locator("td:nth-child(%d)".formatted(index + 1)).allInnerTexts().stream()
                .map(t -> t.replaceAll("\\s+", " ").strip())
                .toList();
    }

    /** The highlighted page number in the pager. */
    public Locator currentPage() {
        return pager.locator("a.page-link.active-page");
    }

    public Locator pageLink(int number) {
        return pager.getByRole(
                AriaRole.LINK,
                new Locator.GetByRoleOptions().setName(Integer.toString(number)).setExact(true));
    }

    /** Highest page number the pager offers (1 when there is no pager, i.e. an empty list). */
    public int lastPageNumber() {
        return pager.locator("a.page-link").allInnerTexts().stream()
                .map(String::strip)
                .filter(t -> t.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(1);
    }
}
