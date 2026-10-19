package com.ppln.automation.ui.pages.dashboard;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.BasePage;
import io.qameta.allure.Step;
import java.util.List;
import java.util.Map;

/**
 * Landing page after login ({@code /<lang_code>/dashboard/}). Confirmed live against ThreatIDR
 * v1.5.1 on 2026-10-17: there is no single stable "dashboard heading" - the first h1/h2/h3/h4 on
 * the page is the logged-in user's display name in the header (varies per role), not a dashboard
 * title. "On the dashboard" is therefore asserted by URL in steps; {@link
 * #authenticatedShellMarker()} is a role-agnostic element present in the sidebar of every
 * authenticated page (used only to detect "login succeeded", not "this is specifically the
 * dashboard").
 *
 * <p>Widget ids (templates/dashboard/index.html, statCard macro): each stat tile has a root {@code
 * #<key>-div}, a percentage {@code #<key>-percentage} and a details modal {@code
 * #<key>-details-modal}. The header, sidebar and footer are the shared layout (sidebar.html,
 * nav.html, footer.html), so their locators work on every authenticated page.
 */
public final class DashboardPage extends BasePage {

    /** Route of the dashboard; also probed over HTTP by {@code StorageStateManager}. */
    public static final String PATH = "/en/dashboard/";

    /** Tile title (as shown) to the id key of its stat card. */
    private static final Map<String, String> TILE_KEYS =
            Map.of(
                    "Total Detection", "total-threat-detection",
                    "C2 Threats Detected", "c2-threats-detected",
                    "Blocked Requests", "stat-card-blocked-requests",
                    "Allowed Requests", "stat-card-allowed-requests");

    /** Panel title to the id of its "no threat found" placeholder. */
    private static final Map<String, String> PANEL_EMPTY_IDS =
            Map.of(
                    "Top Ten Threat Domains", "#top-ten-threat-no-data",
                    "Detected Threat Categories", "#detected-threat-categories-no-data");

    private final Locator authenticatedShellMarker;
    private final Locator filterButton;
    private final Locator filterMenu;
    private final Locator customRangeEntry;
    private final Locator headerUserBlock;
    private final Locator systemIpAddress;
    private final Locator versionLabel;
    private final Locator latestThreatTime;
    private final Locator sidebarLogo;
    private final Locator footer;

    public DashboardPage(Page page) {
        super(page);
        authenticatedShellMarker = page.getByText("System IP Address");
        Locator filterRoot = page.locator("#dashboard-top-filter");
        filterButton =
                filterRoot.getByRole(
                        AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Filter"));
        filterMenu = filterRoot.locator(".dropdown-menu");
        customRangeEntry = page.locator("#custom-date-time-range");
        // the profile toggle in the header: display name over designation (nav.html)
        headerUserBlock = page.locator("#dropdownMenuButton");
        systemIpAddress = page.locator("#bind-server-ip");
        versionLabel = page.locator("#tidr-version");
        latestThreatTime = page.locator("#log-update-time");
        sidebarLogo = page.locator(".sidenav").getByAltText("pipeline_logo");
        footer = page.locator("footer.footer-main");
    }

    @Override
    protected String path() {
        return PATH;
    }

    /**
     * A dashboard widget (tile/chart/panel) identified by its visible heading text - this app has
     * no {@code data-testid}s or other stable structural hooks on these cards, so exact text is the
     * most specific locator tier available. {@code .first()}: chart panels render their title both
     * as a real {@code <h5>} and again inside the Highcharts SVG (an accessibility text node for
     * the chart image), which is a strict-mode duplicate for exact-text matching - confirmed live
     * 2026-10-17 on "Detected Threat Categories"; the first match is always the real heading.
     */
    public Locator widgetLabelled(String text) {
        return page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
    }

    /**
     * An element present on every authenticated page (sidebar footer) and absent on the login
     * screen. Used by {@code StorageStateManager} to decide the session is worth saving.
     */
    public Locator loadedMarker() {
        return authenticatedShellMarker;
    }

    public Locator authenticatedShellMarker() {
        return authenticatedShellMarker;
    }

    // ---- Filter menu ----

    @Step("Choose {label} from the dashboard Filter menu")
    public DashboardPage chooseFilter(String label) {
        filterButton.click();
        filterMenu
                .getByRole(
                        AriaRole.LINK, new Locator.GetByRoleOptions().setName(label).setExact(true))
                .click();
        return this;
    }

    /**
     * Applies a custom range through the "Custom range" picker's own Apply handler. The picker caps
     * dates at today ({@code maxDate: new Date()}, assets/js/scripts/dashboard.js), so a future
     * range cannot be clicked in the calendar; the cap is lifted on the picker instance and its
     * {@code clickApply()} fires the page's real {@code apply.daterangepicker} handler (which
     * reloads every widget and writes {@code ?start=&end=} to the URL).
     *
     * @param start {@code YYYY-MM-DD HH:mm}
     * @param end {@code YYYY-MM-DD HH:mm}
     * @return the error the page's Apply handler threw, or null
     */
    @Step("Apply custom dashboard range {start} - {end}")
    public Object applyCustomRange(String start, String end) {
        filterButton.click();
        customRangeEntry.click();
        return page.evaluate(
                """
                ([start, end]) => {
                  const picker = $('#custom-date-time-range').data('daterangepicker');
                  picker.maxDate = false;
                  picker.setStartDate(new Date(start.replace(' ', 'T')));
                  picker.setEndDate(new Date(end.replace(' ', 'T')));
                  // the handler reloads the widgets, then throws in insertArgsToQueryString
                  // (TypeError: "key" is read-only, global.js) - the page's own error, not ours
                  try { picker.clickApply(); return null; } catch (e) { return String(e); }
                }
                """,
                List.of(start, end));
    }

    // ---- tiles and panels ----

    /** The root card of a stat tile ("Allowed Requests", ...). */
    public Locator tile(String title) {
        return page.locator("#" + tileKey(title) + "-div");
    }

    /** The "x% in last 24h" figure of a stat tile. */
    public Locator tilePercentage(String title) {
        return page.locator("#" + tileKey(title) + "-percentage");
    }

    /** The details modal that belongs to a stat tile. */
    public Locator tileDetailDialog(String title) {
        return page.locator("#" + tileKey(title) + "-details-modal");
    }

    /** The "No threat found yet!" placeholder of a panel; hidden while the panel has data. */
    public Locator panelEmptyState(String title) {
        String id = PANEL_EMPTY_IDS.get(title);
        if (id == null) {
            throw new IllegalArgumentException(
                    "No empty-state placeholder known for panel "
                            + title
                            + "; known: "
                            + PANEL_EMPTY_IDS.keySet());
        }
        return page.locator(id);
    }

    @Step("Click the {title} tile")
    public DashboardPage clickTile(String title) {
        tile(title).click();
        return this;
    }

    private static String tileKey(String title) {
        String key = TILE_KEYS.get(title);
        if (key == null) {
            throw new IllegalArgumentException(
                    "Unknown dashboard tile " + title + "; known: " + TILE_KEYS.keySet());
        }
        return key;
    }

    // ---- shared layout (header, sidebar, footer) ----

    public Locator headerUserBlock() {
        return headerUserBlock;
    }

    public Locator systemIpAddress() {
        return systemIpAddress;
    }

    public Locator versionLabel() {
        return versionLabel;
    }

    public Locator latestThreatTime() {
        return latestThreatTime;
    }

    public Locator sidebarLogo() {
        return sidebarLogo;
    }

    public Locator footer() {
        return footer;
    }
}
