package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.core.reporting.AllureAttachments;
import com.ppln.automation.core.utils.JsRegex;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.pages.components.MessagesComponent;
import com.ppln.automation.ui.pages.settings.AlertListTab;
import com.ppln.automation.ui.pages.settings.EmailTemplatesTab;
import com.ppln.automation.ui.pages.settings.SettingsPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;

/**
 * Settings &gt; Email Template (views/setting/views.py:762-920, settings.js:230-440). The body is a
 * CKEditor 4 "standard" editor in an iframe; opening a template means its Edit button.
 */
public class EmailTemplateSteps {

    private static final String BOLD_TEXT = "QA bold text";
    private static final List<String> LIST_ITEMS = List.of("QA item one", "QA item two");
    private static final String PROBE_TEXT = "QA XSS probe";
    private static final String XSS_FLAG = "__qaXss";

    private final UiScenarioContext ctx;
    private int referencingAlertTypeId;
    private String referencingAlertTypeName;

    public EmailTemplateSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("I create an email template named {string} with bold and list content")
    public void iCreateAnEmailTemplateNamedWithBoldAndListContent(String name) {
        String resolved = ctx.resolve(name);
        SettingsFixtures.deleteEmailTemplateAfterwards(ctx, resolved);
        EmailTemplatesTab tab = tab().openAddForm();
        tab.nameInput().fill(resolved);
        tab.focusBody();
        tab.toolbarButton("bold").click();
        tab.typeIntoBody(BOLD_TEXT);
        tab.toolbarButton("bold").click();
        tab.pressInBody("Enter");
        tab.toolbarButton("bulletedlist").click();
        tab.typeIntoBody(LIST_ITEMS.get(0));
        tab.pressInBody("Enter");
        tab.typeIntoBody(LIST_ITEMS.get(1));
        assertThat(tab.editorBody().locator("strong")).hasText(BOLD_TEXT);
        assertThat(tab.editorBody().locator("ul > li")).hasText(LIST_ITEMS.toArray(String[]::new));
        expectCreated(submitNew(tab));
    }

    @Then("{string} should appear in the email templates list")
    public void shouldAppearInTheEmailTemplatesList(String name) {
        assertThat(reopen().row(ctx.resolve(name))).hasCount(1);
    }

    @Then("opening {string} should show the same formatted content")
    public void openingShouldShowTheSameFormattedContent(String name) {
        EmailTemplatesTab tab = open(ctx.resolve(name));
        assertThat(tab.editorBody().locator("strong")).hasText(BOLD_TEXT);
        assertThat(tab.editorBody().locator("ul > li")).hasText(LIST_ITEMS.toArray(String[]::new));
    }

    /**
     * A template (created over HTTP) plus an alert type that uses it. Cleanup is by id and LIFO, so
     * the alert type goes first (the FK has no ON DELETE), even after a rename.
     */
    @Given("an email template {string} is referenced by an alert type")
    public void anEmailTemplateIsReferencedByAnAlertType(String name) {
        int templateId =
                SettingsFixtures.createEmailTemplate(ctx, ctx.resolve(name), "<p>QA template</p>");
        referencingAlertTypeName = ctx.resolve("QA AT {uniqueString}");
        referencingAlertTypeId =
                SettingsFixtures.createAlertType(ctx, referencingAlertTypeName, templateId);
        reopen(); // the Background opened the tab before these rows existed
    }

    @When("I rename the email template {string} to {string}")
    public void iRenameTheEmailTemplateTo(String name, String newName) {
        EmailTemplatesTab tab = open(ctx.resolve(name));
        tab.nameInput().fill(ctx.resolve(newName));
        CapturedCall call = CapturedCall.of(ctx, "PUT", "**/settings/email-templates/*");
        tab.updateButton().click();
        call.await();
    }

    /**
     * The server's message (asserted exactly) and the success dialog the page shows for it. The
     * page does not display the server's text: it shows its own "Email Template updated
     * successfully" (settings.js:355), so the dialog is matched case-insensitively without the
     * final period.
     */
    @Then("I should see the template message {string}")
    public void iShouldSeeTheTemplateMessage(String text) {
        String expected = ctx.resolve(text);
        HttpResult result = ctx.lastHttp();
        Assertions.assertThat(CapturedCall.json(result).path("message").asText())
                .as("email-templates answer %s", result.body())
                .isEqualTo(expected);
        Pattern shown =
                Pattern.compile(
                        JsRegex.quote(expected.replaceAll("\\.$", "")), Pattern.CASE_INSENSITIVE);
        assertThat(new MessagesComponent(ctx.page()).withText(shown).first()).isVisible();
    }

    @Then("the alert type should still reference {string}")
    public void theAlertTypeShouldStillReference(String name) {
        String resolved = ctx.resolve(name);
        int templateId = SettingsFixtures.templateId(ctx, resolved);
        Assertions.assertThat(
                        ctx.admin()
                                .alertType(referencingAlertTypeId)
                                .path("email_template_id")
                                .asInt())
                .as("email_template_id of alert type %d", referencingAlertTypeId)
                .isEqualTo(templateId);
        SettingsPage settings = new SettingsPage(ctx.page()).open();
        settings.selectTab("Alert List");
        AlertListTab list = settings.alertList();
        assertThat(list.templateCell(referencingAlertTypeName)).hasText(resolved);
    }

    /** Deletes through the UI (Delete, then "Yes, Delete it"); the answer is recorded. */
    @When("I delete the email template {string}")
    public void iDeleteTheEmailTemplate(String name) {
        EmailTemplatesTab tab = tab();
        tab.rowButton(ctx.resolve(name), "Delete").click();
        CapturedCall call = CapturedCall.of(ctx, "DELETE", "**/settings/email-templates/*");
        ctx.page()
                .getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Yes, Delete it").setExact(true))
                .click();
        call.await();
    }

    /**
     * Types a probe text, then appends {@code html} in CKEditor's Source mode (what a user pasting
     * markup does) and creates the template.
     */
    @When("I create an email template named {string} whose content includes {string}")
    public void iCreateAnEmailTemplateNamedWhoseContentIncludes(String name, String html) {
        String resolved = ctx.resolve(name);
        SettingsFixtures.deleteEmailTemplateAfterwards(ctx, resolved);
        EmailTemplatesTab tab = tab().openAddForm();
        tab.nameInput().fill(resolved);
        tab.toolbarButton("source").click();
        tab.sourceArea().fill("<p>" + PROBE_TEXT + "</p>" + html);
        expectCreated(submitNew(tab));
    }

    /**
     * Opens the template in the editor and attaches the evidence of what was tested: the content as
     * stored (the server does not sanitise it) and the images as the editor rendered them.
     */
    @When("I open the email template {string}")
    public void iOpenTheEmailTemplate(String name) {
        String resolved = ctx.resolve(name);
        AllureAttachments.text(
                "Stored template content",
                ctx.admin().emailTemplateContent(SettingsFixtures.templateId(ctx, resolved)));
        EmailTemplatesTab tab = open(resolved);
        assertThat(tab.editorBody()).containsText(PROBE_TEXT);
        // let any injected image finish (fail) loading, so an onerror handler would have run
        ctx.page()
                .waitForCondition(
                        () ->
                                (Boolean)
                                        tab.editorBody()
                                                .evaluate(
                                                        "body => Array.from(body.querySelectorAll('img'))"
                                                                + ".every(i => i.complete)"));
        AllureAttachments.text(
                "Images in the editor",
                String.join(
                        System.lineSeparator(),
                        tab.editorBody()
                                                .locator("img")
                                                .evaluateAll("imgs => imgs.map(i => i.outerHTML)")
                                        instanceof List<?> l
                                ? l.stream().map(String::valueOf).toList()
                                : List.of()));
    }

    /** The probe's handler sets a flag; it must be unset in the page and in every frame. */
    @Then("no script from the template content should have executed")
    public void noScriptFromTheTemplateContentShouldHaveExecuted() {
        List<String> executedIn = new ArrayList<>();
        for (Frame frame : ctx.page().frames()) {
            Object flag =
                    frame.evaluate(
                            "name => window[name] === undefined ? null : String(window[name])",
                            XSS_FLAG);
            if (flag != null) {
                executedIn.add(frame.url());
            }
        }
        Assertions.assertThat(executedIn).as("frames where the template's script ran").isEmpty();
    }

    // ---- helpers ----

    private EmailTemplatesTab tab() {
        return new SettingsPage(ctx.page()).emailTemplates();
    }

    private EmailTemplatesTab reopen() {
        SettingsPage settings = new SettingsPage(ctx.page()).open();
        settings.selectTab("Email Template");
        return settings.emailTemplates();
    }

    /** Opens the template in the editor (Edit) and waits until its name is loaded. */
    private EmailTemplatesTab open(String name) {
        EmailTemplatesTab tab = reopen();
        tab.rowButton(name, "Edit").click();
        assertThat(tab.nameInput()).hasValue(name);
        return tab;
    }

    private HttpResult submitNew(EmailTemplatesTab tab) {
        CapturedCall call = CapturedCall.of(ctx, "POST", "**/settings/email-templates");
        tab.createButton().click();
        return call.await();
    }

    private static void expectCreated(HttpResult result) {
        Assertions.assertThat(CapturedCall.json(result).path("message").asText())
                .as("email-templates answer %s", result.body())
                .isEqualTo("Email Template created successfully.");
    }
}
