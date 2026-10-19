package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.SelectOption;
import com.ppln.automation.core.utils.JsonUtils;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.bdd.support.NotificationFeed;
import com.ppln.automation.ui.bdd.support.NotificationFeed.Push;
import com.ppln.automation.ui.pages.components.MessagesComponent;
import com.ppln.automation.ui.pages.components.NavbarComponent;
import com.ppln.automation.ui.pages.settings.AlertListTab;
import com.ppln.automation.ui.pages.settings.AlertSettingsTab;
import com.ppln.automation.ui.pages.settings.SettingsPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;

/**
 * Settings &gt; Alert Settings (notification recipients), Alert List (alert types) and the header's
 * alert bell (views/setting/views.py:529-706, global.js:156-252).
 */
public class AlertSettingsSteps {

    private static final String ALERT_SETTINGS = "Alert Settings";
    private static final String ALERT_LIST = "Alert List";
    private static final String NO_ALERT = "No Alert";

    /** One notify push arrives per minute; allow for a late Celery beat tick. */
    private static final double PUSH_TIMEOUT_MS = 80_000;

    private static final Pattern EMAIL_VALUE =
            Pattern.compile("id=\"multiple-email\"[^>]*value=\"([^\"]*)\"");

    private final UiScenarioContext ctx;
    private NotificationFeed feed;
    private Set<Long> shownIds;
    private int pushesBeforeOpening;
    private final AtomicReference<String> notificationUpdateBody = new AtomicReference<>();

    public AlertSettingsSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    // ---- Alert Settings ----

    /**
     * Records the stored recipients (server-rendered field) and starts listening to the bell's
     * socket, whose pushes carry the stored "System Notification Sound" flag (never rendered: the
     * box mirrors localStorage). Every save rewrites both, and the recipients always change, so in
     * cleanup unchanged recipients mean nothing was saved. Otherwise the recipients are restored
     * together with the flag from a push; if no push arrives (the environment's Celery workers are
     * not pushing), the flag cannot be known: it is restored as unset/false and the cleanup fails
     * loudly.
     */
    @Given("the original alert settings are recorded for restore")
    public void theOriginalAlertSettingsAreRecordedForRestore() {
        String original = storedRecipients();
        NotificationFeed listener = NotificationFeed.attach(ctx.page());
        new SettingsPage(ctx.page()).open();
        ctx.onCleanup(() -> restoreAlertSettings(original, listener));
    }

    private void restoreAlertSettings(String original, NotificationFeed listener) {
        if (storedRecipients().equals(original)) {
            return; // nothing was saved
        }
        Optional<Boolean> bell = listener.first().map(Push::bellFlag);
        if (bell.isEmpty() && ctx.hasPage()) {
            try {
                bell = Optional.of(listener.awaitPush(0, PUSH_TIMEOUT_MS).bellFlag());
            } catch (RuntimeException e) {
                bell = Optional.empty();
            }
        }
        APIResponse response =
                ctx.admin().saveAlertSettings(tagifyJson(original), bell.orElse(false));
        String now = storedRecipients();
        if (response.status() != 200 || !now.equals(original)) {
            throw new IllegalStateException(
                    "Alert recipients not restored: HTTP %d %s, now '%s', expected '%s'"
                            .formatted(response.status(), response.text(), now, original));
        }
        if (bell.isEmpty()) {
            throw new IllegalStateException(
                    "Alert recipients restored, but the original notification-sound flag could not"
                            + " be read (no bell push arrived); it was saved as false");
        }
    }

    @When("I set the notification email to {string}")
    public void iSetTheNotificationEmailTo(String email) {
        setRecipients(ctx.resolve(email));
    }

    @When("I set the notification emails to {string} and {string}")
    public void iSetTheNotificationEmailsToAnd(String first, String second) {
        setRecipients(ctx.resolve(first), ctx.resolve(second));
    }

    @Then("reloading the settings page should show {string} as the notification email")
    public void reloadingTheSettingsPageShouldShowAsTheNotificationEmail(String email) {
        ctx.awaitPendingCalls();
        Assertions.assertThat(CapturedCall.json(ctx.lastHttp()).path("status").asText())
                .as("alert-settings answer %s", ctx.lastHttp().body())
                .isEqualTo("Ok");
        AlertSettingsTab tab = openTab(ALERT_SETTINGS).alertSettings();
        assertThat(tab.emailTags()).hasText(new String[] {ctx.resolve(email)});
    }

    /** Server error for the recipients field, shown as "Error: &lt;message&gt;" in a dialog. */
    @Then("I should see the alert settings error {string}")
    public void iShouldSeeTheAlertSettingsError(String text) {
        ctx.awaitPendingCalls();
        String expected = ctx.resolve(text);
        Assertions.assertThat(
                        CapturedCall.json(ctx.lastHttp())
                                .path("errors")
                                .path("email")
                                .path(0)
                                .asText())
                .as("alert-settings answer %s", ctx.lastHttp().body())
                .isEqualTo(expected);
        assertThat(new MessagesComponent(ctx.page()).withText(expected).first()).isVisible();
    }

    @Then("{string} should not be stored as a notification recipient")
    public void shouldNotBeStoredAsANotificationRecipient(String email) {
        List<String> stored = Arrays.asList(storedRecipients().split(","));
        Assertions.assertThat(stored).doesNotContain(ctx.resolve(email));
    }

    /**
     * Posts the field the way a non-Tagify client would (CSV), over the scenario's session: the
     * page's Tagify always submits JSON.
     */
    @When("I submit the alert settings with the email value {string}")
    public void iSubmitTheAlertSettingsWithTheEmailValue(String value) {
        SessionHttp.send(
                ctx,
                "POST",
                "/en/settings/alert-settings",
                SessionHttp.withCsrf(ctx.page())
                        .setForm(FormData.create().set("email", ctx.resolve(value))));
    }

    // ---- Alert List ----

    @When("I create an alert type named {string} with period {string}")
    public void iCreateAnAlertTypeNamedWithPeriod(String name, String period) {
        String resolved = ctx.resolve(name);
        SettingsFixtures.deleteAlertTypeAfterwards(ctx, resolved);
        AlertListTab tab = alertList().openAddForm().fill(resolved, ctx.resolve(period));
        HttpResult result = submitNew(tab);
        Assertions.assertThat(CapturedCall.json(result).path("message").asText())
                .as("alert-types answer %s", result.body())
                .isEqualTo("AlertType created successfully.");
    }

    @When("I create an alert type leaving the name empty")
    public void iCreateAnAlertTypeLeavingTheNameEmpty() {
        AlertListTab tab = alertList().openAddForm();
        CapturedCall call = CapturedCall.of(ctx, "POST", "**/settings/alert-types");
        tab.createButton().click();
        Optional<HttpResult> sent = call.await(() -> tab.invalidField("Alert Name").count() > 0);
        call.close();
        Assertions.assertThat(sent).as("create request despite an empty name").isEmpty();
        ctx.useFieldErrors(tab::invalidField);
    }

    @When("I create an alert type named {string} without choosing an email template")
    public void iCreateAnAlertTypeNamedWithoutChoosingAnEmailTemplate(String name) {
        String resolved = ctx.resolve(name);
        SettingsFixtures.deleteAlertTypeAfterwards(ctx, resolved);
        AlertListTab tab = alertList().openAddForm().fill(resolved, NO_ALERT).clearTemplateChoice();
        submitNew(tab);
    }

    /** The name is {uniqueString} padded with "x" to exactly {@code length} characters. */
    @When("I create an alert type whose name is {int} characters long")
    public void iCreateAnAlertTypeWhoseNameIsCharactersLong(int length) {
        String base = ctx.resolve("{uniqueString}");
        String name = base + "x".repeat(Math.max(0, length - base.length()));
        SettingsFixtures.deleteAlertTypeAfterwards(ctx, name);
        AlertListTab tab = alertList().openAddForm().fill(name, NO_ALERT);
        submitNew(tab);
        ctx.useFieldErrors(tab::invalidField);
    }

    @Given("an alert type named {string} exists")
    public void anAlertTypeNamedExists(String name) {
        SettingsFixtures.createAlertType(
                ctx,
                ctx.resolve(name),
                SettingsFixtures.templateId(ctx, SettingsFixtures.DEFAULT_TEMPLATE));
    }

    @When("I change the period of the alert type {string} to {string}")
    public void iChangeThePeriodOfTheAlertTypeTo(String name, String period) {
        String resolved = ctx.resolve(name);
        AlertListTab tab = alertList();
        tab.rowButton(resolved, "Edit").click();
        assertThat(tab.nameInput()).hasValue(resolved); // the edit form has loaded the record
        tab.periodSelect().selectOption(new SelectOption().setLabel(ctx.resolve(period)));
        CapturedCall call = CapturedCall.of(ctx, "PUT", "**/settings/alert-types/*");
        tab.updateButton().click();
        call.await();
    }

    /** The dialog after an Alert List action, and the same text as the server's message. */
    @Then("I should see the alert list message {string}")
    public void iShouldSeeTheAlertListMessage(String text) {
        String expected = ctx.resolve(text);
        Assertions.assertThat(CapturedCall.json(ctx.lastHttp()).path("message").asText())
                .as("alert-types answer %s", ctx.lastHttp().body())
                .isEqualTo(expected);
        assertThat(new MessagesComponent(ctx.page()).withText(expected).first()).isVisible();
    }

    /**
     * After a reload. The list renders the period in minutes ({@code alert_types.html:61}), and the
     * label "No Alert" for 52560000, so the expected cell text is the option's value except for "No
     * Alert".
     */
    @Then("{string} should appear in the alert types list with period {string}")
    public void shouldAppearInTheAlertTypesListWithPeriod(String name, String period) {
        String resolved = ctx.resolve(name);
        String label = ctx.resolve(period);
        AlertListTab tab = openTab(ALERT_LIST).alertList();
        String shown = NO_ALERT.equals(label) ? NO_ALERT : tab.periodValue(label);
        assertThat(tab.row(resolved)).hasCount(1);
        assertThat(tab.periodCell(resolved)).hasText(shown);
    }

    @When("I delete the alert type {string}")
    public void iDeleteTheAlertType(String name) {
        AlertListTab tab = alertList();
        tab.rowButton(ctx.resolve(name), "Delete").click();
        CapturedCall call = CapturedCall.of(ctx, "DELETE", "**/settings/alert-types/*");
        confirmDelete();
        HttpResult result = call.await();
        Assertions.assertThat(result.status()).as(result.body()).isEqualTo(200);
    }

    @Then("{string} should not appear in the alert types list")
    public void shouldNotAppearInTheAlertTypesList(String name) {
        ctx.awaitPendingCalls();
        AlertListTab tab = openTab(ALERT_LIST).alertList();
        assertThat(tab.row(ctx.resolve(name))).hasCount(0);
    }

    // ---- the bell ----

    /**
     * Opens the dashboard with a listener on the bell's socket and waits (up to two pushes) for a
     * push that reports unread alerts; the bell then shows its red dot.
     */
    @Given("at least one unread alert is shown in the bell")
    public void atLeastOneUnreadAlertIsShownInTheBell() {
        feed = NotificationFeed.attach(ctx.page());
        ctx.page().navigate("/en/dashboard/");
        Push push = feed.awaitPush(0, PUSH_TIMEOUT_MS);
        if (push.ids().isEmpty()) {
            push = feed.awaitPush(1, PUSH_TIMEOUT_MS);
        }
        Assertions.assertThat(push.ids())
                .as("unviewed alerts in the bell push (total %d)", push.total())
                .isNotEmpty();
        assertThat(new NavbarComponent(ctx.page()).unreadAlertsDot()).isVisible();
    }

    @When("I open the alert notification feed")
    public void iOpenTheAlertNotificationFeed() {
        pushesBeforeOpening = feed.count();
        shownIds = feed.awaitPush(pushesBeforeOpening - 1, PUSH_TIMEOUT_MS).ids();
        CapturedCall call =
                CapturedCall.guarded(
                        ctx,
                        "POST",
                        "**/dashboard/notification-update",
                        body -> {
                            notificationUpdateBody.set(body);
                            return Optional.empty();
                        });
        new NavbarComponent(ctx.page()).openNotifications();
        HttpResult result = call.await();
        Assertions.assertThat(result.status()).as(result.body()).isEqualTo(200);
    }

    @Then("the page should send a notification update for the alerts shown in the feed")
    public void thePageShouldSendANotificationUpdateForTheAlertsShownInTheFeed() {
        Set<Long> sent = new TreeSet<>();
        JsonUtils.readTree(notificationUpdateBody.get())
                .path("notify_id")
                .forEach(n -> sent.add(Long.parseLong(n.asText())));
        Assertions.assertThat(sent).as("notify_id sent").isEqualTo(shownIds);
        assertThat(new NavbarComponent(ctx.page()).unreadAlertsDot()).isHidden();
    }

    @Then("the next notification push should not list those alerts as unviewed")
    public void theNextNotificationPushShouldNotListThoseAlertsAsUnviewed() {
        Push next = feed.awaitPush(pushesBeforeOpening, PUSH_TIMEOUT_MS);
        Assertions.assertThat(next.ids())
                .as("unviewed ids after opening")
                .doesNotContainAnyElementsOf(shownIds);
    }

    // ---- helpers ----

    private void setRecipients(String... emails) {
        AlertSettingsTab tab = new SettingsPage(ctx.page()).alertSettings();
        tab.clearEmails();
        for (String email : emails) {
            tab.addEmail(email);
        }
        assertThat(tab.emailTags()).hasCount(emails.length);
        // saved by the generic "I click "Save""; outcome steps wait for the answer
        CapturedCall call = CapturedCall.of(ctx, "POST", "**/settings/alert-settings");
        ctx.expectPendingCall(call::await);
    }

    private HttpResult submitNew(AlertListTab tab) {
        CapturedCall call = CapturedCall.of(ctx, "POST", "**/settings/alert-types");
        tab.createButton().click();
        return call.await();
    }

    private void confirmDelete() {
        ctx.page()
                .getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Yes, Delete it").setExact(true))
                .click();
    }

    private AlertListTab alertList() {
        return new SettingsPage(ctx.page()).alertList();
    }

    private SettingsPage openTab(String label) {
        SettingsPage settings = new SettingsPage(ctx.page()).open();
        settings.selectTab(label);
        return settings;
    }

    private String storedRecipients() {
        Matcher m = EMAIL_VALUE.matcher(ctx.admin().settingsHtml());
        if (!m.find()) {
            throw new IllegalStateException("No recipient field on the settings page");
        }
        return m.group(1).replace("&#34;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }

    /** The Tagify JSON the page itself submits for a stored CSV ({@code ""} for none). */
    private static String tagifyJson(String csv) {
        List<String> emails = new ArrayList<>();
        for (String e : csv.split(",")) {
            if (!e.isBlank()) {
                emails.add(e.trim());
            }
        }
        if (emails.isEmpty()) {
            return "";
        }
        ArrayNode tags = JsonUtils.mapper().createArrayNode();
        emails.forEach(e -> tags.addObject().put("value", e));
        return tags.toString();
    }
}
