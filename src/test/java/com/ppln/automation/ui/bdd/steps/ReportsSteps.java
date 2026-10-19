package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.RequestOptions;
import com.ppln.automation.core.reporting.AllureAttachments;
import com.ppln.automation.core.utils.JsonUtils;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.pages.components.MessagesComponent;
import com.ppln.automation.ui.pages.reports.ReportsPage;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.qameta.allure.Allure;
import io.qameta.allure.AttachmentOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.assertj.core.api.Assertions;

/**
 * Reports. "Today" is computed in the JVM's default zone, which matches the browser's because both
 * run on the same host here; if the suite ever runs against a browser in another timezone, pin the
 * context's timezoneId to the JVM's zone rather than loosening this assertion.
 *
 * <p>Generate sends {@code POST /<lang>/reports/csv_download} (JSON rows; the page turns them into
 * a CSV download) or {@code /pdf_download} (writes {@code static/reports/<range>_<type>_report.pdf}
 * and answers {@code {"data": url}}). Steps capture the page's own call and the resulting file.
 */
public class ReportsSteps {

    /** Value of {@code {generatedPdfPath}}: the path of the PDF the last generate step produced. */
    static final String GENERATED_PDF_PATH = "generatedPdfPath";

    private static final DateTimeFormatter RANGE_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** wkhtmltopdf takes about 8 s per summary report on this host; CSV of 25k rows about 3 s. */
    private static final double GENERATE_TIMEOUT_MS = 90_000;

    /**
     * The PDF file name depends only on range and type (tasks/report.py:46-50), not on the
     * language, so concurrent scenarios generating "the last 7 days" overwrite each other's file.
     * Held from Generate until the file has been read. JVM-wide on purpose, like the
     * {@code @serial-*} locks: it guards a file shared by every scenario.
     */
    private static final ReentrantLock PDF_FILE = new ReentrantLock(true);

    private static final Map<String, String> FORMAT_ENDPOINTS =
            Map.of("CSV", "**/reports/csv_download", "Summary", "**/reports/pdf_download");

    private final UiScenarioContext ctx;
    private List<String> csvHeader = List.of();
    private JsonNode csvRows;
    private byte[] pdf;

    public ReportsSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    private ReportsPage page() {
        return new ReportsPage(ctx.page());
    }

    @Given("I am on the reports page")
    public void iAmOnTheReportsPage() {
        new ReportsPage(ctx.page()).open();
    }

    @When("I click the {string} quick-range button")
    public void iClickTheQuickRangeButton(String name) {
        page().clickQuickRange(ctx.resolve(name));
    }

    @Then("the From Date should be {int} days before today")
    public void theFromDateShouldBeDaysBeforeToday(int days) {
        assertThat(page().fromDate()).hasValue(LocalDate.now().minusDays(days).toString());
    }

    @Then("the To Date should be {int} days before today")
    public void theToDateShouldBeDaysBeforeToday(int days) {
        assertThat(page().toDate()).hasValue(LocalDate.now().minusDays(days).toString());
    }

    // ---- range and format, then a generic "I click "Generate"" ----

    @When("I set the report From Date after the To Date")
    public void iSetTheReportFromDateAfterTheToDate() {
        LocalDate today = LocalDate.now();
        page().setRange(today, today.minusDays(1));
    }

    @When("I set the report range to next month")
    public void iSetTheReportRangeToNextMonth() {
        YearMonth next = YearMonth.now().plusMonths(1);
        page().setRange(next.atDay(1), next.atEndOfMonth());
    }

    /**
     * Also starts capturing the call Generate will send, so the outcome steps that follow a generic
     * {@code When I click "Generate"} wait for its answer (see {@code
     * UiScenarioContext#expectPendingCall}).
     */
    @When("I choose the {string} report format")
    public void iChooseTheReportFormat(String label) {
        String format = ctx.resolve(label);
        String endpoint = FORMAT_ENDPOINTS.get(format);
        if (endpoint == null) {
            throw new IllegalArgumentException(
                    "Unknown report format " + format + "; known: " + FORMAT_ENDPOINTS.keySet());
        }
        page().chooseFormatLabelled(format);
        CapturedCall call = CapturedCall.of(ctx, "POST", endpoint);
        ctx.expectPendingCall(
                () -> {
                    try (call) {
                        HttpResult result = call.await(GENERATE_TIMEOUT_MS);
                        AllureAttachments.text(
                                "report answer",
                                "HTTP %d %s".formatted(result.status(), abbreviate(result.body())));
                    }
                });
    }

    // ---- CSV ----

    @When("I generate a CSV report for the last {int} days")
    public void iGenerateACsvReportForTheLastDays(int days) throws IOException {
        LocalDate today = LocalDate.now();
        ReportsPage reports = page().setRange(today.minusDays(days), today).chooseFormat("csv");
        Page browser = ctx.page();
        HttpResult answer;
        Download download;
        try (CapturedCall call = CapturedCall.of(ctx, "POST", "**/reports/csv_download")) {
            download =
                    browser.waitForDownload(
                            new Page.WaitForDownloadOptions().setTimeout(GENERATE_TIMEOUT_MS),
                            reports::generate);
            answer = call.await(GENERATE_TIMEOUT_MS);
        }
        Assertions.assertThat(answer.status())
                .as("csv_download answered %s", abbreviate(answer.body()))
                .isEqualTo(200);
        csvRows = JsonUtils.readTree(answer.body());
        Assertions.assertThat(csvRows.isArray() && !csvRows.isEmpty())
                .as("csv_download rows for the last %d days: %s", days, abbreviate(answer.body()))
                .isTrue();
        String csv = Files.readString(download.path(), StandardCharsets.UTF_8);
        String firstLine = csv.lines().findFirst().orElse("");
        csvHeader = Arrays.stream(firstLine.split(",")).map(String::strip).toList();
        AllureAttachments.text(download.suggestedFilename() + " (header)", firstLine);
    }

    /** Table {@code | column |}: the downloaded CSV's header row has every listed column. */
    @Then("the CSV should contain the columns:")
    public void theCsvShouldContainTheColumns(DataTable table) {
        List<String> expected =
                table.asMaps().stream().map(r -> ctx.resolve(r.get("column"))).toList();
        Assertions.assertThat(csvHeader).as("CSV header").containsAll(expected);
    }

    @Then(
            "every Risk Score should match its Risk \\(Info {int}, Gray {int}, Low {int}, High"
                    + " {int}, Critical {int})")
    public void everyRiskScoreShouldMatchItsRisk(
            int info, int gray, int low, int high, int critical) {
        Map<String, Integer> scores =
                Map.of("Info", info, "Gray", gray, "Low", low, "High", high, "Critical", critical);
        Map<String, Integer> mismatches = new HashMap<>();
        for (JsonNode row : csvRows) {
            String risk = row.path("Risk").asText();
            Integer expected = scores.get(risk);
            if (expected == null || expected != row.path("Risk Score").asInt(-1)) {
                mismatches.merge(risk + " -> " + row.path("Risk Score").asText(), 1, Integer::sum);
            }
        }
        Assertions.assertThat(mismatches)
                .as(
                        "Risk -> Risk Score pairs (with row counts) outside the mapping, %d rows",
                        csvRows.size())
                .isEmpty();
    }

    /** Reports the share as an attachment only: whether empty values are acceptable is open. */
    @Then("the share of rows with an empty Asn and Operator should be attached to the report")
    public void theShareOfRowsWithAnEmptyAsnAndOperatorShouldBeAttachedToTheReport() {
        int total = csvRows.size();
        long empty = 0;
        for (JsonNode row : csvRows) {
            if (row.path("Asn").asText().isBlank() && row.path("Operator").asText().isBlank()) {
                empty++;
            }
        }
        Assertions.assertThat(total).as("CSV rows").isPositive();
        AllureAttachments.text(
                "Asn/Operator completeness",
                "%d of %d rows (%.1f%%) have an empty Asn and Operator"
                        .formatted(empty, total, 100.0 * empty / total));
    }

    /** As the current session, straight to the endpoint (no Reports page needed). */
    @When("I request the CSV report download directly")
    public void iRequestTheCsvReportDownloadDirectly() {
        LocalDate today = LocalDate.now();
        String range = today.minusDays(7).format(RANGE_DATE) + "-" + today.format(RANGE_DATE);
        Page page = ctx.page();
        RequestOptions options = SessionHttp.withCsrf(page).setData(Map.of("range", range));
        SessionHttp.send(ctx, "POST", "/en/reports/csv_download", options);
    }

    // ---- PDF ----

    /**
     * Generates through the Reports page of {@code lang} ({@code /ja/reports/} sends {@code "lang":
     * "ja"}), waits for the answer and the success dialog, reads the PDF and stores its path as
     * {@code {generatedPdfPath}}.
     */
    @When("I generate a {string} PDF report for the last {int} days in language {string}")
    public void iGenerateAPdfReportForTheLastDaysInLanguage(String type, int days, String lang) {
        String language = ctx.resolve(lang);
        LocalDate today = LocalDate.now();
        ReportsPage reports = new ReportsPage(ctx.page(), language);
        if (!ctx.page().url().contains("/" + language + "/reports/")) {
            reports.open();
        }
        reports.setRange(today.minusDays(days), today).chooseFormat(ctx.resolve(type));
        PDF_FILE.lock();
        try {
            HttpResult answer;
            try (CapturedCall call = CapturedCall.of(ctx, "POST", "**/reports/pdf_download")) {
                reports.generate();
                answer = call.await(GENERATE_TIMEOUT_MS);
            }
            JsonNode body = CapturedCall.json(answer);
            Assertions.assertThat(body.path("data").asText())
                    .as(
                            "pdf_download answered HTTP %d %s",
                            answer.status(), abbreviate(answer.body()))
                    .endsWith("_" + ctx.resolve(type) + "_report.pdf");
            assertThat(new MessagesComponent(ctx.page()).confirmButton()).isVisible();
            String path = URI.create(body.path("data").asText()).getPath();
            ctx.put(GENERATED_PDF_PATH, path);
            APIResponse file = ctx.page().request().get(path);
            Assertions.assertThat(file.status()).as("GET %s", path).isEqualTo(200);
            pdf = file.body();
        } finally {
            PDF_FILE.unlock();
        }
        Allure.attachment(
                "report-" + language + ".pdf",
                "application/pdf",
                new ByteArrayInputStream(pdf),
                AttachmentOptions.withFileExtension(".pdf"));
        Assertions.assertThat(
                        new String(pdf, 0, Math.min(5, pdf.length), StandardCharsets.US_ASCII))
                .as("PDF signature")
                .isEqualTo("%PDF-");
    }

    @Then("the downloaded PDF should have at least one page of extractable text")
    public void theDownloadedPdfShouldHaveAtLeastOnePageOfExtractableText() throws IOException {
        try (PDDocument document = PDDocument.load(requirePdf())) {
            int pages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            int pagesWithText = 0;
            for (int i = 1; i <= pages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                if (stripper.getText(document).codePoints().anyMatch(Character::isLetter)) {
                    pagesWithText++;
                }
            }
            Assertions.assertThat(pagesWithText)
                    .as("pages with extractable text out of %d (%s)", pages, fontSummary(document))
                    .isPositive();
        }
    }

    /**
     * The text layer holds Japanese (Hiragana, Katakana or CJK ideographs) and no replacement or
     * "tofu" characters (U+FFFD, U+25A1), which is what a missing glyph turns into.
     */
    @Then("the PDF should contain Japanese characters, not missing-glyph boxes")
    public void thePdfShouldContainJapaneseCharactersNotMissingGlyphBoxes() throws IOException {
        try (PDDocument document = PDDocument.load(requirePdf())) {
            String text = new PDFTextStripper().getText(document);
            long japanese = text.codePoints().filter(ReportsSteps::isJapanese).count();
            Assertions.assertThat(japanese)
                    .as(
                            "Japanese characters in the text layer of %d pages (%s); text starts: %s",
                            document.getNumberOfPages(),
                            fontSummary(document),
                            abbreviate(text.strip()))
                    .isPositive();
            Assertions.assertThat(text).as("missing-glyph characters").doesNotContain("�", "□");
        }
    }

    // ---- helpers ----

    private byte[] requirePdf() {
        if (pdf == null) {
            throw new IllegalStateException("No PDF was generated in this scenario");
        }
        return pdf;
    }

    private static boolean isJapanese(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HAN;
    }

    private static String fontSummary(PDDocument document) throws IOException {
        int fonts = 0;
        for (var page : document.getPages()) {
            for (var ignored : page.getResources().getFontNames()) {
                fonts++;
            }
        }
        return fonts + " font resources";
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
