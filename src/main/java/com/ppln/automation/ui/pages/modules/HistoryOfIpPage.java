package com.ppln.automation.ui.pages.modules;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.ppln.automation.ui.pages.BasePage;

/**
 * History of IP ({@code /<lang>/history_of_ip/}, no menu entry). Written against the template the
 * build ships as {@code _history_of_ip/index.html} (the view renders {@code history_of_ip/}, which
 * is missing, so the live page is an HTTP 500): {@code input#ip} holds the looked-up address and
 * {@code #history_table1} lists Domains, Count, Timestamp, Source IP, Country, Log Type, Blocked.
 */
public final class HistoryOfIpPage extends BasePage {

    private final Locator lookupField;
    private final Locator rows;

    public HistoryOfIpPage(Page page) {
        super(page);
        lookupField = page.locator("#ip");
        rows = page.locator("#history_table1 tbody tr");
    }

    @Override
    protected String path() {
        return "/en/history_of_ip/";
    }

    public Locator lookupField() {
        return lookupField;
    }

    public Locator rows() {
        return rows;
    }

    /** Domains column of every row. */
    public Locator domainCells() {
        return rows.locator("td:nth-child(1)");
    }

    /** Source IP column of every row. */
    public Locator sourceIpCells() {
        return rows.locator("td:nth-child(4)");
    }
}
