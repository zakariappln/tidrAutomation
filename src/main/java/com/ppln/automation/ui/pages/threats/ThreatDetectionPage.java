package com.ppln.automation.ui.pages.threats;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.BasePage;
import io.qameta.allure.Step;
import java.util.List;

/**
 * Threat Detection ({@code /<lang_code>/threats/}; the blueprint and page title still say
 * "Discover"). Confirmed live 2026-10-17: "Risk Heatmap" is an {@code <h2>}; the threats list is a
 * DataTables table with id {@code discover-table} (columns: Timestamp, Source IP, Destination
 * Domain, Destination IP, Threat Type, Category, Risk, Risk Score, Assess Frequency - three more
 * than on 2026-09-27).
 *
 * <p>Filters (templates/discover/components/allThreatsTable.html, checked 2026-10-19): {@code
 * <select>}s with ids {@code domain_count}, {@code threat}, {@code risk_category} and no label,
 * hidden behind custom "styledSelect" lists, a daterangepicker on {@code
 * #all-threat-table-date-range} with the presets Today / Yesterday / Last 7 Day / Last 30 Day, and
 * an icon-only sort toggle {@code #sort-asc-desc}. Select changes take effect only on "Apply
 * Filter"; the sort toggle reloads the table itself. The page renders no search box (the markup is
 * commented out and the DataTables {@code dom} has no filter control).
 */
public final class ThreatDetectionPage extends BasePage {

    /** Column headers in table order. */
    public static final List<String> COLUMNS =
            List.of(
                    "Timestamp",
                    "Source IP",
                    "Destination Domain",
                    "Destination IP",
                    "Threat Type",
                    "Category",
                    "Risk",
                    "Risk Score",
                    "Assess Frequency");

    private final Locator threatsTable;
    private final Locator dataRows;
    private final Locator emptyRow;
    private final Locator domainCountSelect;
    private final Locator threatSelect;
    private final Locator riskCategorySelect;
    private final Locator applyFilterButton;
    private final Locator sortToggle;
    private final Locator dateRange;
    private final Locator infoLabel;
    private final Locator pager;
    private final Locator heatmap;

    public ThreatDetectionPage(Page page) {
        super(page);
        threatsTable = page.locator("#discover-table");
        dataRows =
                threatsTable
                        .locator("tbody tr")
                        .filter(
                                new Locator.FilterOptions()
                                        .setHasNot(page.locator("td.dataTables_empty")));
        emptyRow = threatsTable.locator("tbody td.dataTables_empty");
        domainCountSelect = page.locator("#domain_count");
        threatSelect = page.locator("#threat");
        riskCategorySelect = page.locator("#risk_category");
        applyFilterButton =
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Apply Filter"));
        // icon-only button without an accessible name: its id is the only stable hook
        sortToggle = page.locator("#sort-asc-desc");
        dateRange = page.locator("#all-threat-table-date-range");
        // ids DataTables derives from the table id
        infoLabel = page.locator("#discover-table_info");
        pager = page.locator("#discover-table_paginate");
        heatmap = page.locator("#cal-heatmap");
    }

    @Override
    protected String path() {
        return "/en/threats/";
    }

    public Locator heading(String name) {
        return page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    public Locator threatsTable() {
        return threatsTable;
    }

    /** Data rows of the current page (the "No data" placeholder row excluded). */
    public Locator dataRows() {
        return dataRows;
    }

    /** The placeholder cell DataTables shows when the table has no rows. */
    public Locator emptyRow() {
        return emptyRow;
    }

    /** Cells of one column on the current page, by header text (see {@link #COLUMNS}). */
    public Locator column(String header) {
        int index = COLUMNS.indexOf(header);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown column " + header + "; known: " + COLUMNS);
        }
        return dataRows.locator("td:nth-child(" + (index + 1) + ")");
    }

    public Locator infoLabel() {
        return infoLabel;
    }

    public Locator heatmap() {
        return heatmap;
    }

    /** Month labels the calendar heatmap renders, one per month domain. */
    public Locator heatmapMonthLabels() {
        return heatmap.locator(".ch-domain-text");
    }

    @Step("Select domain count {label}")
    public ThreatDetectionPage selectDomainCount(String label) {
        choose(domainCountSelect, label);
        return this;
    }

    @Step("Select threat {label}")
    public ThreatDetectionPage selectThreat(String label) {
        choose(threatSelect, label);
        return this;
    }

    @Step("Select risk category {label}")
    public ThreatDetectionPage selectRiskCategory(String label) {
        choose(riskCategorySelect, label);
        return this;
    }

    /** Picks one of the date-range presets ("Today", "Last 7 Day", ...); applies immediately. */
    @Step("Choose date range preset {preset}")
    public ThreatDetectionPage chooseDatePreset(String preset) {
        dateRange.click();
        page.locator(".daterangepicker .ranges")
                .getByText(preset, new Locator.GetByTextOptions().setExact(true))
                .click();
        return this;
    }

    /**
     * The native selects are hidden: templates/discover/index.html:330-410 puts a {@code
     * div.styledSelect#styledSelect-<id>} over each and lists the options as {@code <li>}s in the
     * {@code ul.options} right after it (select2 is initialised too, but sits underneath). Picking
     * an {@code <li>} sets the select's value.
     */
    private void choose(Locator select, String label) {
        String styled = "#styledSelect-" + select.getAttribute("id");
        page.locator(styled).click();
        page.locator(styled + " + ul.options li")
                .getByText(label, new Locator.GetByTextOptions().setExact(true))
                .click();
    }

    @Step("Apply the table filters")
    public ThreatDetectionPage applyFilters() {
        applyFilterButton.click();
        return this;
    }

    @Step("Toggle the sort order")
    public ThreatDetectionPage toggleSort() {
        sortToggle.click();
        return this;
    }

    @Step("Go to page {number} of the threats table")
    public ThreatDetectionPage goToPage(int number) {
        pager.getByRole(
                        AriaRole.LINK,
                        new Locator.GetByRoleOptions()
                                .setName(String.valueOf(number))
                                .setExact(true))
                .click();
        return this;
    }
}
