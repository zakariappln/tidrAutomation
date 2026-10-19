package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.ppln.automation.core.reporting.AllureAttachments;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.bdd.support.TarGzArchive;
import com.ppln.automation.ui.fixtures.AdminHttp.UserRecord;
import com.ppln.automation.ui.pages.settings.SettingsPage;
import com.ppln.automation.ui.pages.settings.SupportTab;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;

/**
 * Settings &gt; User Log, System Log, Help &amp; Support and Send Diagnostics. The diagnostics
 * bundle carries system logs: it is read in memory only, and only its file listing is attached.
 */
public class LogsSupportSteps {

    private static final String DIAGNOSTICS_PATH = "/en/settings/send-diagnostics";
    private static final int ADMIN_USER_ID = 1;

    /** Credentials as they would appear in a config or log line; values never reported. */
    private static final List<Pattern> CREDENTIALS =
            List.of(
                    Pattern.compile("(?i)postgres(?:ql)?://[^:/\\s]+:[^@\\s]+@"),
                    Pattern.compile("(?i)redis://[^@\\s]*:[^@\\s]+@"),
                    Pattern.compile(
                            "(?i)\\b(?:DB_PASSWORD|REDIS_PASS|requirepass)\\b\\s*[:=]?\\s*\\S+"),
                    Pattern.compile("(?im)^\\s*password\\s*[:=]\\s*\\S+"));

    /** Top-level sections the app strips from the grok config before bundling it. */
    private static final Pattern STRIPPED_SECTIONS = Pattern.compile("(?m)^(database|redis)\\s*:");

    private final UiScenarioContext ctx;
    private TarGzArchive archive;
    private byte[] downloaded;

    public LogsSupportSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    /** The row is the one {@code the user log should contain:} filtered to (unique domain). */
    @Then("the user log entry for {string} should name the admin as {string}")
    public void theUserLogEntryForShouldNameTheAdminAs(String domain, String column) {
        Assertions.assertThat(ctx.resolve(column)).as("column").isEqualTo("Created By");
        SettingsPage settings = new SettingsPage(ctx.page());
        String resolved = ctx.resolve(domain);
        settings.filterUserLog(resolved);
        Locator row = settings.userLogRow(resolved, "User Blocked", "Create");
        UserRecord admin = ctx.admin().user(ADMIN_USER_ID).orElseThrow();
        assertThat(settings.userLogCreatedBy(row))
                .hasText(admin.firstName() + " " + admin.lastName());
    }

    @Then("I should see the {string} and {string} fields")
    public void iShouldSeeTheAndFields(String first, String second) {
        for (String label : new String[] {ctx.resolve(first), ctx.resolve(second)}) {
            assertThat(
                            ctx.page()
                                    .getByLabel(label)
                                    .filter(new Locator.FilterOptions().setVisible(true)))
                    .hasCount(1);
        }
    }

    /**
     * Every link in the Help &amp; Support pane answers below 400 (mailto/tel/javascript links are
     * not requests). In this build the pane has no links at all, so the check passes vacuously; the
     * count is attached to the report.
     */
    @Then("every link on the help tab should answer with a non-error status")
    public void everyLinkOnTheHelpTabShouldAnswerWithANonErrorStatus() {
        SupportTab support = new SettingsPage(ctx.page()).support();
        assertThat(support.root()).isVisible();
        List<String> hrefs = new ArrayList<>();
        for (Locator link : support.links().all()) {
            hrefs.add(link.getAttribute("href"));
        }
        AllureAttachments.text("Help & Support links", hrefs.size() + " link(s): " + hrefs);
        List<String> broken = new ArrayList<>();
        for (String href : hrefs) {
            if (href.matches("(?i)^(mailto|tel|javascript):.*") || href.startsWith("#")) {
                continue;
            }
            APIResponse response = ctx.page().request().get(href);
            if (response.status() >= 400) {
                broken.add(href + " -> " + response.status());
            }
        }
        Assertions.assertThat(broken).as("links answering an error").isEmpty();
    }

    /**
     * Clicks "Get Diagnostics" on the Help &amp; Support tab. The button opens the download in a
     * new window, so the answer is captured at the browser-context level (passed through unchanged)
     * and kept in memory.
     */
    @When("I trigger Send Diagnostics")
    public void iTriggerSendDiagnostics() {
        SettingsPage settings = new SettingsPage(ctx.page()).open();
        settings.selectTab("Help & Support");
        AtomicReference<byte[]> body = new AtomicReference<>();
        Consumer<Route> capture =
                route -> {
                    APIResponse response = route.fetch(new Route.FetchOptions().setTimeout(60_000));
                    byte[] bytes = response.body();
                    route.fulfill(new Route.FulfillOptions().setResponse(response));
                    body.set(bytes); // only after fulfil: unroute must not race a running handler
                };
        ctx.browserContext().route("**" + DIAGNOSTICS_PATH, capture);
        try {
            settings.support().getDiagnosticsButton().click();
            ctx.page()
                    .waitForCondition(
                            () -> body.get() != null,
                            new Page.WaitForConditionOptions().setTimeout(60_000));
        } finally {
            ctx.browserContext().unroute("**" + DIAGNOSTICS_PATH, capture);
        }
        downloaded = body.get();
    }

    @Then("the downloaded archive should be a readable tar.gz")
    public void theDownloadedArchiveShouldBeAReadableTarGz() {
        Assertions.assertThat(downloaded).as("downloaded bytes").isNotEmpty();
        archive = TarGzArchive.read(downloaded);
        AllureAttachments.text("Diagnostics archive listing", archive.listing());
        Assertions.assertThat(archive.files()).as("files in the archive").isNotEmpty();
    }

    @Then("the archive should contain {string}")
    public void theArchiveShouldContain(String fileName) {
        Assertions.assertThat(archive.containsFileNamed(ctx.resolve(fileName)))
                .as("archive listing:%n%s", archive.listing())
                .isTrue();
    }

    @Then("the archive should not contain database or redis credentials")
    public void theArchiveShouldNotContainDatabaseOrRedisCredentials() {
        List<String> findings = new ArrayList<>();
        for (Map.Entry<String, byte[]> file : archive.files().entrySet()) {
            String text = new String(file.getValue(), StandardCharsets.UTF_8);
            if (file.getKey().endsWith("grok-exporter-config.yml")
                    && STRIPPED_SECTIONS.matcher(text).find()) {
                findings.add(file.getKey() + ": database/redis section present");
            }
            for (Pattern p : CREDENTIALS) {
                if (p.matcher(text).find()) {
                    findings.add(file.getKey() + ": matches " + p.pattern());
                }
            }
        }
        Assertions.assertThat(findings).as("credential findings (values not shown)").isEmpty();
    }

    /**
     * {@code GET /en/settings/send-diagnostics} over the scenario's session. A binary answer is
     * recorded as a short summary, never its bytes.
     */
    @When("I request Send Diagnostics directly")
    public void iRequestSendDiagnosticsDirectly() {
        APIResponse response = ctx.page().request().get(DIAGNOSTICS_PATH);
        String type = response.headers().getOrDefault("content-type", "");
        String body =
                type.startsWith("text/") || type.contains("json")
                        ? response.text()
                        : "<%s, %d bytes, final URL %s>"
                                .formatted(
                                        type,
                                        response.body().length,
                                        URI.create(response.url()).getPath());
        ctx.recordHttp(new HttpResult("GET", DIAGNOSTICS_PATH, response.status(), body));
    }
}
