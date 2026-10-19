package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.pages.system.UpdateSystemPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;

/**
 * Update System rejection paths (views/home/views.py:455-560). Only uploads the server must refuse
 * are sent: a valid patch would hold the 10-minute upload lock, and {@code apply_update} runs a
 * shell script from an extracted zip.
 *
 * <p>Uploads go "to the update endpoint": a multipart POST over the browser session, exactly what
 * the page's form sends (the form's own size check would stop an oversize file client-side). The
 * server answers with a redirect back to the page and a flashed message, which the browser then
 * loads, so the message is asserted on the real page.
 */
public class UpdateSystemSteps {

    private static final String UPDATE_PATH = "/en/update";

    /** 8 MB (decimal): above the app's 7 MiB limit, below nginx's client_max_body_size 8m. */
    private static final int EIGHT_MB = 8_000_000;

    private final UiScenarioContext ctx;

    public UpdateSystemSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("I upload a text file renamed to {string} to the update endpoint")
    public void iUploadATextFileRenamedToToTheUpdateEndpoint(String fileName) {
        upload(
                new FilePayload(
                        ctx.resolve(fileName),
                        "application/zip",
                        "QA: plain text, not a zip archive\n".getBytes(StandardCharsets.UTF_8)));
    }

    @When("I upload {string} to the update endpoint")
    public void iUploadToTheUpdateEndpoint(String fileName) {
        upload(
                new FilePayload(
                        ctx.resolve(fileName),
                        "application/octet-stream",
                        "QA upload fixture\n".getBytes(StandardCharsets.UTF_8)));
    }

    @When("I upload an 8MB zip file to the update endpoint")
    public void iUploadAn8mbZipFileToTheUpdateEndpoint() {
        upload(new FilePayload("qa-oversize-patch.zip", "application/zip", new byte[EIGHT_MB]));
    }

    @Then("I should see the upload message {string}")
    public void iShouldSeeTheUploadMessage(String text) {
        HttpResult result = ctx.lastHttp();
        Assertions.assertThat(result.status())
                .as("upload answer (a redirect carrying the flashed message): %s", result.body())
                .isBetween(300, 399);
        assertThat(new UpdateSystemPage(ctx.page()).message()).hasText(ctx.resolve(text));
    }

    /**
     * {@code POST /en/apply_update/<name>} with the name exactly as given (already URL-encoded in
     * the feature). Safe for any name that is not an uploaded tidr patch zip: the server only runs
     * a script after extracting a real zip from its patch folder.
     */
    @When("I request apply_update for the name {string}")
    public void iRequestApplyUpdateForTheName(String name) {
        new UpdateSystemPage(ctx.page()).open();
        SessionHttp.send(ctx, "POST", "/en/apply_update/" + ctx.resolve(name), null);
    }

    @Then("the apply response should not contain {string}")
    public void theApplyResponseShouldNotContain(String text) {
        HttpResult result = ctx.lastHttp();
        Assertions.assertThat(result.body())
                .as("apply_update answer, HTTP %d", result.status())
                .doesNotContain(ctx.resolve(text));
    }

    // ---- helpers ----

    private void upload(FilePayload file) {
        UpdateSystemPage page = new UpdateSystemPage(ctx.page()).open();
        String token = SessionHttp.csrfToken(page.page());
        APIResponse response =
                page.page()
                        .request()
                        .post(
                                UPDATE_PATH,
                                RequestOptions.create()
                                        .setMaxRedirects(0)
                                        .setMultipart(
                                                FormData.create()
                                                        .set("csrf_token", token)
                                                        .set("file", file)));
        String location = response.headers().getOrDefault("location", "");
        String body =
                response.status() / 100 == 3
                        ? "redirect to " + location
                        : abbreviate(response.text());
        ctx.recordHttp(new HttpResult("POST", UPDATE_PATH, response.status(), body));
        if (response.status() / 100 == 3) {
            page.page().navigate(location); // shows the flashed message (session cookie is shared)
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
