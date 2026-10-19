package com.ppln.automation.ui.pages.audit;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.BasePage;
import io.qameta.allure.Step;
import java.util.List;

/**
 * Access log ({@code /<lang_code>/access_log/}, {@code templates/access_log/index.html}). Built
 * from source only: the blueprint is not registered on build 1.5.1, so the URL redirects to the
 * dashboard and this page has never been seen live. Filter form: IP Address input, the shared
 * date-range picker ({@code .date-range-picker}, predefined ranges "Last 7 Days", "Last 30 Days",
 * ...) and a Refresh button; the server-side DataTable {@code #accesslog-table} has columns Time
 * Stamp, Browser, Operating System, IP, Count (one row per client IP).
 */
public final class AccessLogPage extends BasePage {

    private final Locator ipAddressInput;
    private final Locator dateRangePicker;
    private final Locator refreshButton;
    private final Locator table;

    public AccessLogPage(Page page) {
        super(page);
        ipAddressInput = page.getByPlaceholder("IP Address");
        // the picker is a styled div with no role or label
        dateRangePicker = page.locator("#accesslog-filter-form .date-range-picker");
        refreshButton =
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Refresh"));
        table = page.locator("#accesslog-table");
    }

    @Override
    protected String path() {
        return "/en/access_log/";
    }

    public Locator table() {
        return table;
    }

    public Locator columnHeader(String title) {
        return table.locator("thead")
                .getByRole(
                        AriaRole.COLUMNHEADER,
                        new Locator.GetByRoleOptions().setName(title).setExact(true));
    }

    @Step("Filter the access log by {rangeLabel} and client IP {ip}")
    public AccessLogPage filter(String rangeLabel, String ip) {
        ipAddressInput.fill(ip);
        dateRangePicker.click();
        // daterangepicker renders its ranges as <li> items in a body-level .daterangepicker popup
        page.locator(".daterangepicker .ranges li")
                .filter(new Locator.FilterOptions().setHasText(rangeLabel))
                .click();
        refreshButton.click();
        return this;
    }

    /** Text of every body cell in the column headed {@code title}. */
    public List<String> columnValues(String title) {
        List<String> titles =
                table.locator("thead th").allInnerTexts().stream().map(String::strip).toList();
        int index = titles.indexOf(title);
        if (index < 0) {
            throw new IllegalArgumentException("No column '%s' in %s".formatted(title, titles));
        }
        return table
                .locator("tbody tr td:nth-child(%d)".formatted(index + 1))
                .allInnerTexts()
                .stream()
                .map(String::strip)
                .toList();
    }
}
