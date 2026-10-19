package com.ppln.automation.ui.pages.settings;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.BasePage;
import io.qameta.allure.Step;

/**
 * Settings ({@code /<lang_code>/settings/}): one page of Bootstrap tabs ({@code role="tab"} links:
 * General Settings, Alert Settings, Alert List, Email Template, API Integration, Help &amp;
 * Support, User Log, System Log, ...), each shown only to users holding its permission.
 *
 * <p>User Log ({@code templates/setting/partials/alert_settings/logs.html}) is a client-side
 * DataTable ({@code #logTable}, 20 rows per page, newest first) over the whole Blocked/Allowed
 * history; columns Domain, "User Blocked|Allowed", "Create|Delete", Created By, Date.
 */
public final class SettingsPage extends BasePage {

    private final Locator userLogTable;

    public SettingsPage(Page page) {
        super(page);
        userLogTable = page.locator("#logTable");
    }

    @Override
    protected String path() {
        return "/en/settings/";
    }

    public Locator tab(String label) {
        return page.getByRole(
                AriaRole.TAB, new Page.GetByRoleOptions().setName(label).setExact(true));
    }

    @Step("Open the {label} settings tab")
    public SettingsPage selectTab(String label) {
        tab(label).click();
        return this;
    }

    /**
     * Narrows the User Log to rows mentioning {@code text}. The table is configured without a
     * search box ({@code dom: 't<...p><...i>'}), so DataTables' own search API is the only way to
     * reach rows on later pages (DataTables detaches them from the DOM); paging through 20 rows at
     * a time instead would be order-dependent on a log every scenario writes to. The partial loads
     * a second jQuery, so the table is reached through the {@code DataTable} global rather than
     * {@code $}. If DataTables never initialised (its CDN script failed), every row is still in the
     * DOM and nothing needs narrowing.
     */
    @Step("Filter the User Log to {text}")
    public SettingsPage filterUserLog(String text) {
        userLogTable.evaluate(
                """
                (table, text) => {
                  const DT = window.DataTable;
                  if (DT && DT.isDataTable(table)) {
                    new DT.Api(table).search(text).draw();
                  }
                }""",
                text);
        return this;
    }

    public GeneralSettingsTab general() {
        return new GeneralSettingsTab(page);
    }

    public AlertSettingsTab alertSettings() {
        return new AlertSettingsTab(page);
    }

    public AlertListTab alertList() {
        return new AlertListTab(page);
    }

    public EmailTemplatesTab emailTemplates() {
        return new EmailTemplatesTab(page);
    }

    public SyslogTab syslog() {
        return new SyslogTab(page);
    }

    public SupportTab support() {
        return new SupportTab(page);
    }

    /** User Log row cells: Domain, Event Type, Action Type, Created By, Date. */
    public Locator userLogCreatedBy(Locator row) {
        return row.locator("td").nth(3);
    }

    /** Visible User Log rows. */
    public Locator userLogRows() {
        return userLogTable.locator("tbody tr");
    }

    /** User Log rows with exactly these Domain, Event Type and Action Type cells. */
    public Locator userLogRow(String domain, String eventType, String actionType) {
        Locator rows = userLogRows();
        for (String cell : new String[] {domain, eventType, actionType}) {
            rows =
                    rows.filter(
                            new Locator.FilterOptions()
                                    .setHas(
                                            page.getByRole(
                                                    AriaRole.CELL,
                                                    new Page.GetByRoleOptions()
                                                            .setName(cell)
                                                            .setExact(true))));
        }
        return rows;
    }
}
