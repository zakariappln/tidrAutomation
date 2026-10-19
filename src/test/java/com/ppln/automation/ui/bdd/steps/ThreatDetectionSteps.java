package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Response;
import com.ppln.automation.core.utils.JsonUtils;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.pages.threats.ThreatDetectionPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;

/**
 * Threat Detection: filters, sort, paging and the Risk Heatmap. Every step that reloads the table
 * captures the page's own {@code POST /<lang>/threats/data} (DataTables server-side call) and the
 * assertions check the rendered rows against that answer and the filters it was sent with - never
 * against fixed counts, because live DNS traffic keeps adding threats.
 */
public class ThreatDetectionSteps {

    private static final DateTimeFormatter ROW_TIME =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.ENGLISH);

    /** Date-range presets of the table's daterangepicker (allThreatsTable/dateFilter.js). */
    private static final Map<Integer, String> DATE_PRESETS =
            Map.of(7, "Last 7 Day", 30, "Last 30 Day");

    private final UiScenarioContext ctx;

    /** The table call made by the last step that reloaded the table. */
    private TableCall lastCall;

    /** Form fields sent and JSON answered by one {@code threats/data} call. */
    private record TableCall(Map<String, String> sent, JsonNode answer) {
        int start() {
            return Integer.parseInt(sent.getOrDefault("start", "0"));
        }

        JsonNode rows() {
            return answer.path("data");
        }
    }

    public ThreatDetectionSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    private ThreatDetectionPage page() {
        return new ThreatDetectionPage(ctx.page());
    }

    @Given("I am on the threat detection page")
    public void iAmOnTheThreatDetectionPage() {
        new ThreatDetectionPage(ctx.page()).open();
    }

    @Then("I should see the {string}")
    public void iShouldSeeTheHeading(String heading) {
        assertThat(page().heading(ctx.resolve(heading))).isVisible();
    }

    @Then("I should see the threats table")
    public void iShouldSeeTheThreatsTable() {
        assertThat(page().threatsTable()).isVisible();
    }

    // ---- filters ----

    @When("I set the domain count filter to {string}")
    public void iSetTheDomainCountFilterTo(String label) {
        page().selectDomainCount(ctx.resolve(label));
        reloadTable(() -> page().applyFilters());
    }

    @When("I set the date range to the last {int} days")
    public void iSetTheDateRangeToTheLastDays(int days) {
        String preset = DATE_PRESETS.get(days);
        if (preset == null) {
            throw new IllegalArgumentException(
                    "The date picker offers presets for " + DATE_PRESETS.keySet() + " days only");
        }
        page().chooseDatePreset(preset);
        reloadTable(() -> page().applyFilters());
    }

    @When("I filter the threats table by risk category {string}")
    public void iFilterTheThreatsTableByRiskCategory(String category) {
        page().selectRiskCategory(ctx.resolve(category));
        reloadTable(() -> page().applyFilters());
        Assertions.assertThat(lastCall.sent().get("risk_category"))
                .as("risk_category sent")
                .isEqualTo(ctx.resolve(category));
    }

    @When("I filter the threats table by threat {string}")
    public void iFilterTheThreatsTableByThreat(String threat) {
        page().selectThreat(ctx.resolve(threat));
        reloadTable(() -> page().applyFilters());
        Assertions.assertThat(lastCall.sent().get("threat")).as("threat sent").isNotBlank();
    }

    @Then("every row shown should be dated within the last {int} days")
    public void everyRowShownShouldBeDatedWithinTheLastDays(int days) {
        // The preset covers today-(days-1) .. today in the browser's zone; rows are printed in
        // the server's stored time (UTC here), which can be a day behind - hence the bounds.
        LocalDate today = LocalDate.now();
        LocalDate earliest = today.minusDays(days);
        String sentDate = lastCall.sent().getOrDefault("date", "");
        Assertions.assertThat(sentDate)
                .as("date range sent with the table call")
                .startsWith(
                        today.minusDays(days - 1L)
                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        for (LocalDateTime time : rowTimes()) {
            Assertions.assertThat(time.toLocalDate())
                    .as("row dated %s, range sent %s", time, sentDate)
                    .isBetween(earliest, today);
        }
    }

    @Then("every row shown should have at most {int} hits for its domain on that day")
    public void everyRowShownShouldHaveAtMostHitsForItsDomainOnThatDay(int hits) {
        Assertions.assertThat(lastCall.sent().get("domain_count"))
                .as("domain_count sent")
                .isEqualTo(String.valueOf(hits));
        assertRowsMatchAnswer();
        for (String frequency : page().column("Assess Frequency").allInnerTexts()) {
            Assertions.assertThat(Integer.parseInt(frequency.strip()))
                    .as("Assess Frequency of a row")
                    .isLessThanOrEqualTo(hits);
        }
    }

    @Then("every row shown should have threat type {string} or the table should be empty")
    public void everyRowShownShouldHaveThreatTypeOrTheTableShouldBeEmpty(String threatType) {
        assertEveryRowOrEmpty("Threat Type", ctx.resolve(threatType));
    }

    @Then("every row shown should have risk {string} or the table should be empty")
    public void everyRowShownShouldHaveRiskOrTheTableShouldBeEmpty(String risk) {
        assertEveryRowOrEmpty("Risk", ctx.resolve(risk));
    }

    private void assertEveryRowOrEmpty(String column, String expected) {
        assertRowsMatchAnswer();
        if (lastCall.rows().isEmpty()) {
            assertThat(page().emptyRow()).isVisible();
            return;
        }
        for (String value : page().column(column).allInnerTexts()) {
            Assertions.assertThat(value.strip()).as("%s of a row", column).isEqualTo(expected);
        }
    }

    // ---- sort ----

    @Then("the threats table should be ordered by time newest first")
    public void theThreatsTableShouldBeOrderedByTimeNewestFirst() {
        assertThat(page().dataRows().first()).isVisible();
        Assertions.assertThat(rowTimes()).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @When("I toggle the threats table sort order")
    public void iToggleTheThreatsTableSortOrder() {
        reloadTable(() -> page().toggleSort());
    }

    @Then("the threats table should be ordered by time oldest first")
    public void theThreatsTableShouldBeOrderedByTimeOldestFirst() {
        Assertions.assertThat(lastCall.sent().get("short_by")).as("short_by sent").isEqualTo("asc");
        assertRowsMatchAnswer();
        Assertions.assertThat(rowTimes()).isNotEmpty().isSorted();
    }

    // ---- paging ----

    @Given("the threats table has more than one page of rows")
    public void theThreatsTableHasMoreThanOnePageOfRows() {
        assertThat(page().dataRows().first()).isVisible();
        int shown = page().dataRows().count();
        Matcher m = Pattern.compile("of ([\\d,]+) entries").matcher(page().infoLabel().innerText());
        Assertions.assertThat(m.find())
                .as("info label %s", page().infoLabel().innerText())
                .isTrue();
        Assertions.assertThat(number(m.group(1))).as("total entries").isGreaterThan(shown);
    }

    @When("I go to page {int} of the threats table")
    public void iGoToPageOfTheThreatsTable(int number) {
        reloadTable(() -> page().goToPage(number));
    }

    /**
     * {@code template} is the label's wording with X, Y and Z standing for the numbers, e.g.
     * "Showing X to Y of Z entries": X must be the first row's position, Y the last row's, and Z
     * the total the same table call reported.
     */
    @Then("the {string} label should match the rows displayed")
    public void theLabelShouldMatchTheRowsDisplayed(String template) {
        assertRowsMatchAnswer();
        String regex =
                Pattern.quote(ctx.resolve(template))
                        .replace("X", "\\E([\\d,]+)\\Q")
                        .replace("Y", "\\E([\\d,]+)\\Q")
                        .replace("Z", "\\E([\\d,]+)\\Q");
        String label = page().infoLabel().innerText().strip();
        Matcher m = Pattern.compile("^" + regex + "$").matcher(label);
        Assertions.assertThat(m.matches()).as("label '%s' against '%s'", label, template).isTrue();
        int rows = page().dataRows().count();
        int first = lastCall.start() + 1;
        Assertions.assertThat(number(m.group(1)))
                .as("X (first row) in '%s'", label)
                .isEqualTo(first);
        Assertions.assertThat(number(m.group(2)))
                .as("Y (last row) in '%s' with %d rows shown", label, rows)
                .isEqualTo(first + rows - 1);
        Assertions.assertThat(number(m.group(3)))
                .as("Z (total) in '%s'", label)
                .isEqualTo(lastCall.answer().path("recordsFiltered").asInt());
    }

    // ---- heatmap ----

    /**
     * The heatmap is painted once from {@code /threats/show-threat-year-month-chart} (the last 365
     * days) as 12 month domains ending with the current month, and the table filter does not touch
     * it: after a table reload it must still show those 12 months.
     */
    @Then("the Risk Heatmap should cover the last {int} days")
    public void theRiskHeatmapShouldCoverTheLastDays(int days) {
        int months = (int) Math.round(days / 30.4);
        YearMonth current = YearMonth.now();
        List<String> expected = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            expected.add(
                    current.minusMonths(i)
                            .getMonth()
                            .getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
        }
        assertThat(page().heatmapMonthLabels()).hasText(expected.toArray(String[]::new));
        // the data source of the heatmap (public route, last 365 days by design)
        var series = ctx.page().request().get("/en/threats/show-threat-year-month-chart");
        Assertions.assertThat(series.status()).as("heatmap series status").isEqualTo(200);
        LocalDate oldestAllowed = LocalDate.now().minusDays(days + 1L);
        for (JsonNode point : JsonUtils.readTree(series.text())) {
            Assertions.assertThat(LocalDate.parse(point.path("t").asText()))
                    .as("heatmap point %s", point)
                    .isAfterOrEqualTo(oldestAllowed);
        }
    }

    // ---- helpers ----

    /** Runs {@code action} and keeps the {@code threats/data} call it triggers. */
    private void reloadTable(Runnable action) {
        Response response =
                ctx.page()
                        .waitForResponse(
                                r ->
                                        r.url().contains("/threats/data")
                                                && "POST".equals(r.request().method()),
                                action);
        Assertions.assertThat(response.status())
                .as("threats/data answered %s", response.text())
                .isEqualTo(200);
        lastCall =
                new TableCall(
                        form(response.request().postData()), JsonUtils.readTree(response.text()));
    }

    /** The table shows exactly the rows of the last call (same count, same first domain). */
    private void assertRowsMatchAnswer() {
        Assertions.assertThat(lastCall).as("a step reloaded the table").isNotNull();
        int expected = lastCall.rows().size();
        assertThat(page().dataRows()).hasCount(expected);
        if (expected > 0) {
            assertThat(page().column("Destination Domain").first())
                    .hasText(lastCall.rows().get(0).path("query_domain").asText());
        }
    }

    private List<LocalDateTime> rowTimes() {
        return page().column("Timestamp").allInnerTexts().stream()
                .map(t -> LocalDateTime.parse(t.strip(), ROW_TIME))
                .toList();
    }

    private static int number(String digits) {
        return Integer.parseInt(digits.replace(",", ""));
    }

    private static Map<String, String> form(String postData) {
        Map<String, String> fields = new HashMap<>();
        if (postData == null) {
            return fields;
        }
        for (String pair : postData.split("&")) {
            String[] kv = pair.split("=", 2);
            fields.put(
                    URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return fields;
    }
}
