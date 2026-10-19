package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.ppln.automation.core.reporting.AllureAttachments;
import com.ppln.automation.core.utils.JsRegex;
import com.ppln.automation.core.utils.JsonUtils;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.fixtures.AdminHttp;
import com.ppln.automation.ui.pages.dashboard.DashboardPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;

/**
 * Dashboard widgets, the Filter menu, stat-tile percentages and the shared header/sidebar/footer
 * shown on the dashboard. Widget data comes from POST {@code /<lang>/dashboard/<widget>} calls the
 * page sends itself (assets/js/scripts/dashboard.js); steps that change the range record those
 * calls and assert on what was sent and answered.
 */
public class DashboardSteps {

    /**
     * Widget calls that carry the selected range ({@code fetchData()} in dashboard.js); {@code
     * dashboard-statistics} is always posted with {@code {}} and is not one of them.
     */
    private static final List<String> RANGED_WIDGETS =
            List.of(
                    "c2-threats-detected",
                    "total-threats-detected",
                    "blocked-threats-detected",
                    "allowed-requests",
                    "top-ten-threat-domains",
                    "dns-activity-trend",
                    "detected-threat-categories");

    private static final Pattern IPV4 =
            Pattern.compile(
                    "^\\s*((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)\\s*$");

    /** {@code format_timedelta(..., add_direction=True)} in latest_threat_update_time.py. */
    private static final Pattern RELATIVE_TIME =
            Pattern.compile("^\\s*\\d+ (second|minute|hour|day|week|month|year)s? ago\\s*$");

    private final UiScenarioContext ctx;

    /** Widget calls sent after the step that changed the range (finished requests only). */
    private final List<Request> widgetCalls = new CopyOnWriteArrayList<>();

    private boolean recording;

    public DashboardSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    private DashboardPage page() {
        return new DashboardPage(ctx.page());
    }

    // ---- widgets ----

    @Then("I should see the {string} tile")
    public void iShouldSeeTheTile(String name) {
        assertThat(page().widgetLabelled(name)).isVisible();
    }

    @Then("I should see the {string} chart")
    public void iShouldSeeTheChart(String name) {
        assertThat(page().widgetLabelled(name)).isVisible();
    }

    @Then("I should see the {string} panel")
    public void iShouldSeeThePanel(String name) {
        assertThat(page().widgetLabelled(name)).isVisible();
    }

    @When("I reload the dashboard")
    public void iReloadTheDashboard() {
        page().open();
    }

    // ---- Filter menu / ranges ----

    @When("I choose {string} from the dashboard Filter menu")
    public void iChooseFromTheDashboardFilterMenu(String label) {
        recordWidgetCalls();
        page().chooseFilter(ctx.resolve(label));
    }

    /**
     * A range starting 30 days from now. The picker itself refuses future dates, so the range is
     * applied through the picker's own Apply handler (see {@link DashboardPage#applyCustomRange}).
     * Waits until both list panels have been reloaded for that range.
     */
    @When("I choose a custom dashboard range entirely in the future")
    public void iChooseACustomDashboardRangeEntirelyInTheFuture() {
        LocalDate from = LocalDate.now().plusDays(30);
        LocalDate to = from.plusDays(30);
        recordWidgetCalls();
        Object pageError = page().applyCustomRange(from + " 00:00", to + " 23:59");
        if (pageError != null) {
            AllureAttachments.text(
                    "error thrown by the page's custom-range handler", pageError.toString());
        }
        for (String widget : List.of("top-ten-threat-domains", "detected-threat-categories")) {
            Request call = awaitWidgetCall(widget);
            JsonNode sent = JsonUtils.readTree(call.postData());
            Assertions.assertThat(sent.path("start_date").asText())
                    .as("%s start_date", widget)
                    .startsWith(from.toString());
            Response response = call.response();
            Assertions.assertThat(response.status()).as("%s status", widget).isEqualTo(200);
            Assertions.assertThat(JsonUtils.readTree(response.text()).size())
                    .as("%s rows for a future range: %s", widget, response.text())
                    .isZero();
        }
    }

    @Then("the dashboard widget requests should be sent with range {string}")
    public void theDashboardWidgetRequestsShouldBeSentWithRange(String range) {
        String expected = ctx.resolve(range);
        for (String widget : RANGED_WIDGETS) {
            Request call = awaitWidgetCall(widget);
            JsonNode sent = JsonUtils.readTree(call.postData());
            Assertions.assertThat(sent.path("range").asText())
                    .as("range sent to %s (body %s)", widget, call.postData())
                    .isEqualTo(expected);
            Assertions.assertThat(call.response().status())
                    .as("%s answered", widget)
                    .isEqualTo(200);
        }
    }

    @Then("the {string} panel should show {string}")
    public void thePanelShouldShow(String panel, String text) {
        var emptyState = page().panelEmptyState(ctx.resolve(panel));
        assertThat(emptyState).isVisible();
        assertThat(emptyState).hasText(ctx.resolve(text));
    }

    private void recordWidgetCalls() {
        widgetCalls.clear();
        if (!recording) {
            recording = true;
            ctx.page()
                    .onRequestFinished(
                            r -> {
                                if ("POST".equals(r.method()) && r.url().contains("/dashboard/")) {
                                    widgetCalls.add(r);
                                }
                            });
        }
    }

    private Request awaitWidgetCall(String widget) {
        try {
            ctx.page().waitForCondition(() -> widgetCall(widget).isPresent());
        } catch (TimeoutError e) {
            Assertions.fail(
                    "No %s call after the range change; calls seen: %s",
                    widget,
                    widgetCalls.stream().map(Request::url).collect(Collectors.joining(", ")));
        }
        return widgetCall(widget).orElseThrow();
    }

    private Optional<Request> widgetCall(String widget) {
        return widgetCalls.stream()
                .filter(r -> r.url().endsWith("/dashboard/" + widget))
                .findFirst();
    }

    // ---- tile percentages ----

    /**
     * Reloads the dashboard, keeps the {@code dashboard-statistics} answer the page itself received
     * (the 24-hour counts move with live traffic, so a separate request could disagree) and
     * recomputes what getDashboardStatistics.js renders: average = 30_days_summery / 29 (1 when
     * that is 0), percentage = (24_hours_summery - average) / average * 100, displayed as {@code
     * Math.abs(p.toFixed(2))}% with one arrow (up when positive), or "0%" with both arrows when
     * either input is 0.
     */
    @Then(
            "the {string} percentage change should match the {string} values from dashboard-statistics")
    public void thePercentageChangeShouldMatchTheValuesFromDashboardStatistics(
            String tile, String statistic) {
        HttpResult answer;
        try (CapturedCall call =
                CapturedCall.of(ctx, "POST", "**/dashboard/dashboard-statistics")) {
            page().open();
            answer = call.await();
        }
        Assertions.assertThat(answer.status()).as("dashboard-statistics status").isEqualTo(200);
        JsonNode stat = JsonUtils.readTree(answer.body()).path(ctx.resolve(statistic));
        Assertions.assertThat(stat.isObject()).as("%s in %s", statistic, answer.body()).isTrue();
        long today = stat.path("24_hours_summery").asLong();
        long last30 = stat.path("30_days_summery").asLong();
        double average = last30 > 0 ? last30 / 29.0 : 1;

        var percentage = page().tilePercentage(ctx.resolve(tile));
        var upArrow = percentage.locator("i.fa-arrow-up");
        var downArrow = percentage.locator("i.fa-arrow-down");
        if (today == 0) {
            assertThat(percentage).hasText(Pattern.compile("^\\s*0%\\s*$"));
            assertThat(upArrow).hasCount(1);
            assertThat(downArrow).hasCount(1);
            return;
        }
        double change = (today - average) / average * 100;
        String shown =
                new BigDecimal(change)
                        .setScale(2, RoundingMode.HALF_UP)
                        .abs()
                        .stripTrailingZeros()
                        .toPlainString();
        AllureAttachments.text(
                "dashboard-statistics " + statistic,
                "24_hours_summery=%d, 30_days_summery=%d, average=%s, change=%s -> %s%%"
                        .formatted(today, last30, average, change, shown));
        assertThat(percentage)
                .hasText(Pattern.compile("^\\s*" + JsRegex.quote(shown + "%") + "\\s*$"));
        assertThat(change > 0 ? upArrow : downArrow).hasCount(1);
        assertThat(change > 0 ? downArrow : upArrow).hasCount(0);
    }

    // ---- tile drill-down ----

    @When("I click the {string} tile")
    public void iClickTheTile(String tile) {
        page().clickTile(ctx.resolve(tile));
    }

    @Then("the {string} detail dialog should open with a table")
    public void theDetailDialogShouldOpenWithATable(String tile) {
        var dialog = page().tileDetailDialog(ctx.resolve(tile));
        assertThat(dialog).isVisible();
        assertThat(dialog.locator("table").first()).isVisible();
    }

    // ---- header ----

    @Then("the header user block should not contain the text {string}")
    public void theHeaderUserBlockShouldNotContainTheText(String text) {
        var block = page().headerUserBlock();
        assertThat(block).isVisible();
        assertThat(block).not().containsText(ctx.resolve(text));
    }

    @Then("the header should show the designation {string}")
    public void theHeaderShouldShowTheDesignation(String designation) {
        assertThat(page().headerUserBlock()).containsText(ctx.resolve(designation));
    }

    /**
     * Sets the designation of the account the scenario is logged in as (a fixture or disposable
     * user, never the admin) over the admin's HTTP session; the stored profile is restored
     * afterwards.
     */
    @Given("my profile designation is set to {string}")
    public void myProfileDesignationIsSetTo(String designation) {
        if (!ctx.has(FixtureUserSteps.CURRENT_USER_ID)) {
            throw new IllegalStateException(
                    "Profile edits must run as a fixture or disposable user, not "
                            + ctx.get(AuthSteps.CURRENT_USERNAME));
        }
        int userId = Integer.parseInt(ctx.get(FixtureUserSteps.CURRENT_USER_ID));
        AdminHttp.UserRecord original =
                ctx.restoreAfterwards(
                        () -> ctx.admin().user(userId).orElseThrow(),
                        stored -> ctx.admin().updateUser(userId, AdminHttp.editForm(stored)));
        Map<String, String> form = new HashMap<>(AdminHttp.editForm(original));
        form.put("designation", ctx.resolve(designation));
        var response = ctx.admin().updateUser(userId, form);
        Assertions.assertThat(response.status())
                .as("update_user answered %s", response.text())
                .isEqualTo(200);
        Assertions.assertThat(ctx.admin().user(userId).orElseThrow().designation())
                .as("stored designation")
                .isEqualTo(ctx.resolve(designation));
    }

    // ---- sidebar / branding ----

    @Then("the sidebar should show the {string} with an IPv4 value")
    public void theSidebarShouldShowTheWithAnIpv4Value(String label) {
        assertThat(
                        ctx.page()
                                .locator(".sidenav")
                                .getByText(
                                        ctx.resolve(label),
                                        new Locator.GetByTextOptions().setExact(true)))
                .isVisible();
        assertThat(page().systemIpAddress()).hasText(IPV4);
    }

    /** {@code "v1.5.1"}: the sidebar reads "ThreatIDR Ver. &lt;version&gt;" (sidebar.html). */
    @Then("the sidebar should show the ThreatIDR version {string}")
    public void theSidebarShouldShowTheThreatIdrVersion(String version) {
        String number = ctx.resolve(version).replaceFirst("^[vV]", "");
        assertThat(page().versionLabel())
                .hasText(
                        Pattern.compile(
                                "^\\s*ThreatIDR\\s+(v|Ver\\.?\\s*)"
                                        + JsRegex.quote(number)
                                        + "\\s*$"));
    }

    @Then("the {string} time should be shown")
    public void theTimeShouldBeShown(String label) {
        assertThat(
                        ctx.page()
                                .getByText(
                                        ctx.resolve(label),
                                        new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        assertThat(page().latestThreatTime()).hasText(RELATIVE_TIME);
    }

    @Then("the ThreatIDR logo should be visible in the sidebar")
    public void theThreatIdrLogoShouldBeVisibleInTheSidebar() {
        assertThat(page().sidebarLogo()).isVisible();
    }

    @Then("the footer should show {string}")
    public void theFooterShouldShow(String text) {
        assertThat(page().footer()).isVisible();
        assertThat(page().footer()).containsText(ctx.resolve(text));
    }
}
