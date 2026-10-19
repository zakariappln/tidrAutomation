package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Page;
import com.ppln.automation.core.config.Credentials;
import com.ppln.automation.ui.auth.LoginRateLimitGuard;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.bdd.support.UploadFiles;
import com.ppln.automation.ui.fixtures.AdminHttp;
import com.ppln.automation.ui.fixtures.FixtureUsers;
import com.ppln.automation.ui.pages.auth.LoginPage;
import com.ppln.automation.ui.pages.users.UsersPage;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;

/**
 * Users tab: create (with outcome capture), edit, search, delete, card/list view. Users created
 * through the UI are deleted over the admin API in cleanup, keyed by the id the server returned;
 * preconditions ("a non-admin user exists") are created over the admin API.
 */
public class UsersSteps {

    private static final String REGISTER_GLOB = "**/users/register";
    private static final String UPDATE_GLOB = "**/users/update_user/*";
    private static final Pattern NEW_USER_ID = Pattern.compile("pipeline-user-lists-(\\d+)");
    private static final String VALID_PASSWORD = "Ab1!xyzz";

    private final UiScenarioContext ctx;

    /** Values typed into the last create form and what the server answered (empty: never sent). */
    private Map<String, String> lastTyped;

    private Optional<HttpResult> lastCreate = Optional.empty();

    /** "that user": the precondition user of this scenario. */
    private AdminHttp.UserRecord thatUser;

    private Page otherSession;

    public UsersSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    private UsersPage page() {
        return new UsersPage(ctx.page());
    }

    @Given("I am on the users page")
    public void iAmOnTheUsersPage() {
        new UsersPage(ctx.page()).open();
    }

    // ---- create through the UI ----

    /** Table with a header row {@code | field | value |}; values may contain placeholders. */
    @When("I create a user with:")
    public void iCreateAUserWith(DataTable table) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Map<String, String> row : table.asMaps()) {
            fields.put(row.get("field"), ctx.resolve(row.get("value")));
        }
        submitCreate(fields, null);
        assertCreated();
    }

    @Then("the user {string} should appear in the users list")
    public void theUserShouldAppearInTheUsersList(String email) {
        String resolved = ctx.resolve(email);
        // the list is paged (9, oldest first): filter by the created user's last name
        UsersPage list = page().openSearch(lastTyped.get("Last Name"));
        assertThat(list.userCard(resolved)).isVisible();
    }

    @When("I create a user leaving {string} empty")
    public void iCreateAUserLeavingEmpty(String field) {
        Map<String, String> fields = validUser();
        fields.remove(ctx.resolve(field));
        submitCreate(fields, null);
    }

    @When("I create a user with {string} set to a {int}-character value")
    public void iCreateAUserWithSetToACharacterValue(String field, int length) {
        Map<String, String> fields = validUser();
        String name = ctx.resolve(field);
        String value =
                switch (name) {
                    case "Email" -> emailOfLength(length);
                    case "First Name", "Last Name", "Title" ->
                            capitalised(FixtureUsers.randomLetters(length));
                    default ->
                            throw new IllegalArgumentException("No length rule for field " + name);
                };
        fields.put(name, value);
        submitCreate(fields, null);
        ctx.put("__fieldUnderTest", name);
    }

    @When("I create a user with email {string}")
    public void iCreateAUserWithEmail(String email) {
        Map<String, String> fields = validUser();
        fields.put("Email", ctx.resolve(email));
        submitCreate(fields, null);
        ctx.put("__fieldUnderTest", "Email");
    }

    @When("I create a user with password {string}")
    public void iCreateAUserWithPassword(String password) {
        iCreateAUserWithPasswordAndConfirmPassword(password, password);
    }

    @When("I create a user with password {string} and confirm password {string}")
    public void iCreateAUserWithPasswordAndConfirmPassword(String password, String confirm) {
        Map<String, String> fields = validUser();
        fields.put("Password", ctx.resolve(password));
        fields.put("Confirm Password", ctx.resolve(confirm));
        submitCreate(fields, null);
    }

    @When("I create a user with phone {string}")
    public void iCreateAUserWithPhone(String phone) {
        Map<String, String> fields = validUser();
        fields.put("Phone", ctx.resolve(phone));
        submitCreate(fields, null);
        ctx.put("__fieldUnderTest", "Phone");
    }

    @When("I create a user with the photo {string}")
    public void iCreateAUserWithThePhoto(String file) {
        submitCreate(validUser(), UploadFiles.get(ctx.resolve(file)));
    }

    /**
     * "accepted": the server answered "Ok" and stored exactly what was typed. "rejected": nothing
     * was stored with the typed values - the browser refused to send the form, the server answered
     * with errors, or (maxlength truncation) the stored value differs from the typed one.
     */
    @Then("the user creation should be {string}")
    public void theUserCreationShouldBe(String expected) {
        switch (ctx.resolve(expected).toLowerCase(Locale.ROOT)) {
            case "accepted" -> assertStoredAsTyped(assertCreated());
            case "rejected" -> assertNotStoredAsTyped();
            default ->
                    throw new IllegalArgumentException(
                            "Expected accepted or rejected: " + expected);
        }
    }

    @Then("I should see the user form error {string}")
    public void iShouldSeeTheUserFormError(String message) {
        assertThat(
                        page().visibleCreateUserErrors()
                                .filter(
                                        new com.microsoft.playwright.Locator.FilterOptions()
                                                .setHasText(ctx.resolve(message))))
                .hasCount(1);
    }

    @Then("I should see a password complexity error")
    public void iShouldSeeAPasswordComplexityError() {
        assertThat(page().createUserError("Password"))
                .containsText(Pattern.compile("[Pp]assword must be at least 8 characters"));
        assertThat(page().createUserError("Password")).isVisible();
        lastCreate.ifPresent(
                r ->
                        Assertions.assertThat(CapturedCall.json(r).path("status").asText())
                                .as("no user is created with a weak password")
                                .isNotEqualTo("Ok"));
    }

    private void submitCreate(Map<String, String> fields, Path photo) {
        lastTyped = fields;
        UsersPage users = page();
        try (CapturedCall call = CapturedCall.of(ctx, "POST", REGISTER_GLOB)) {
            users.createUser(fields, photo);
            lastCreate = call.await(() -> users.visibleCreateUserErrors().count() > 0);
        }
        ctx.useFieldErrors(field -> new UsersPage(ctx.page()).createUserError(field));
        lastCreate
                .flatMap(UsersSteps::createdId)
                .ifPresent(id -> ctx.onCleanup(() -> ctx.admin().deleteUser(id)));
    }

    private static Optional<Integer> createdId(HttpResult result) {
        JsonNode body = CapturedCall.json(result);
        Matcher m = NEW_USER_ID.matcher(body.path("user").asText(""));
        return "Ok".equals(body.path("status").asText()) && m.find()
                ? Optional.of(Integer.parseInt(m.group(1)))
                : Optional.empty();
    }

    private int assertCreated() {
        Assertions.assertThat(lastCreate).as("the create-user request was sent").isPresent();
        HttpResult result = lastCreate.get();
        JsonNode body = CapturedCall.json(result);
        Assertions.assertThat(body.path("message").asText())
                .as("register answered HTTP %d: %s", result.status(), result.body())
                .isEqualTo("User created successfully.");
        return createdId(result).orElseThrow();
    }

    private void assertStoredAsTyped(int id) {
        AdminHttp.UserRecord stored = ctx.admin().user(id).orElseThrow();
        Assertions.assertThat(stored.email()).isEqualTo(lastTyped.get("Email"));
        Assertions.assertThat(stored.firstName()).isEqualTo(lastTyped.get("First Name"));
        Assertions.assertThat(stored.lastName()).isEqualTo(lastTyped.get("Last Name"));
        if (lastTyped.containsKey("Phone")) {
            // the widget submits "+<dial code> <typed>"
            Assertions.assertThat(stored.phone()).endsWith(lastTyped.get("Phone"));
        }
    }

    private void assertNotStoredAsTyped() {
        if (lastCreate.isEmpty()) {
            assertThat(page().visibleCreateUserErrors().first()).isVisible();
            return;
        }
        HttpResult result = lastCreate.get();
        Assertions.assertThat(result.status())
                .as("a rejection is an answer, not a crash: %s", result.body())
                .isLessThan(500);
        Optional<Integer> id = createdId(result);
        if (id.isEmpty()) {
            return; // the server refused it
        }
        String field = ctx.has("__fieldUnderTest") ? ctx.get("__fieldUnderTest") : "Email";
        AdminHttp.UserRecord stored = ctx.admin().user(id.get()).orElseThrow();
        String storedValue =
                switch (field) {
                    case "Email" -> stored.email();
                    case "First Name" -> stored.firstName();
                    case "Last Name" -> stored.lastName();
                    case "Phone" -> stored.phone();
                    default -> stored.designation();
                };
        Assertions.assertThat(storedValue)
                .as("a user was created; its %s must not be the rejected value", field)
                .isNotEqualTo(lastTyped.get(field))
                .doesNotEndWith(" " + lastTyped.get(field));
    }

    /** A valid create form with unique, letters-only names (the list search finds them). */
    private Map<String, String> validUser() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("First Name", "Qa");
        fields.put("Last Name", "Qauser" + FixtureUsers.randomLetters(8));
        fields.put("Email", ctx.resolve("{uniqueEmail}"));
        fields.put("Password", VALID_PASSWORD);
        fields.put("Confirm Password", VALID_PASSWORD);
        fields.put("Title", "QA Engineer");
        return fields;
    }

    private static String emailOfLength(int length) {
        String domain = "@example.com";
        int local = length - domain.length();
        if (local < 1) {
            throw new IllegalArgumentException("Email length must exceed " + domain.length());
        }
        return FixtureUsers.randomLetters(local) + domain;
    }

    private static String capitalised(String letters) {
        return letters.isEmpty()
                ? letters
                : Character.toUpperCase(letters.charAt(0)) + letters.substring(1);
    }

    // ---- preconditions ----

    @Given("a non-admin user exists")
    public void aNonAdminUserExists() {
        createThatUser();
    }

    @Given("a user exists with a unique letters-only last name")
    public void aUserExistsWithAUniqueLettersOnlyLastName() {
        createThatUser();
    }

    /**
     * Created as admin with a designation and 3+ letter names (the edit form requires both), on the
     * "Read Reports" fixture role.
     */
    private Credentials createThatUser() {
        AdminHttp admin = ctx.admin();
        FixtureUsers.Disposable user =
                FixtureUsers.createDisposable(admin, FixtureUsers.readReportsRoleId(admin));
        ctx.onCleanup(() -> ctx.admin().deleteUser(user.userId()));
        thatUser = admin.user(user.userId()).orElseThrow();
        return user.credentials();
    }

    /** One real login in a second browser session (the scenario is tagged @serial-auth). */
    @Given("a non-admin user exists and is logged in in a separate session")
    public void aNonAdminUserExistsAndIsLoggedInInASeparateSession() {
        Credentials credentials = createThatUser();
        otherSession = ctx.openIsolatedSession(null);
        boolean reserved = LoginRateLimitGuard.heldByCurrentThread();
        if (!reserved) {
            LoginRateLimitGuard.acquire();
        }
        try {
            var dashboard =
                    new LoginPage(otherSession)
                            .<LoginPage>open()
                            .loginAs(credentials.username(), credentials.password());
            assertThat(dashboard.loadedMarker()).isVisible();
        } finally {
            if (!reserved) {
                LoginRateLimitGuard.release();
            }
        }
    }

    // ---- edit / delete / search ----

    @When("I edit that user's phone number to {string}")
    public void iEditThatUsersPhoneNumberTo(String phone) {
        UsersPage users = page().openSearch(thatUser.lastName()).openEdit(thatUser.email());
        assertThat(users.editEmailInput()).hasValue(thatUser.email()); // form data loaded
        try (CapturedCall call = CapturedCall.of(ctx, "PUT", UPDATE_GLOB)) {
            users.updatePhone(ctx.resolve(phone));
            call.await();
        }
        ctx.useFieldErrors(field -> new UsersPage(ctx.page()).editUserError(field));
    }

    @Then("reloading the user should show the phone number {string}")
    public void reloadingTheUserShouldShowThePhoneNumber(String phone) {
        HttpResult update = ctx.lastHttp();
        Assertions.assertThat(CapturedCall.json(update).path("message").asText())
                .as("update answered HTTP %d: %s", update.status(), update.body())
                .isEqualTo("User updated successfully");
        UsersPage users = page().openSearch(thatUser.lastName());
        assertThat(users.userCard(thatUser.email())).containsText(ctx.resolve(phone));
    }

    @When("I delete that user")
    public void iDeleteThatUser() {
        UsersPage users = page().openSearch(thatUser.lastName());
        try (CapturedCall call = CapturedCall.of(ctx, "DELETE", UPDATE_GLOB)) {
            users.removeUser(thatUser.email());
            HttpResult result = call.await();
            Assertions.assertThat(CapturedCall.json(result).path("message").asText())
                    .as("delete answered HTTP %d: %s", result.status(), result.body())
                    .isEqualTo("User deleted successfully");
        }
        assertThat(users.userCard(thatUser.email())).hasCount(0);
    }

    @Then("their active session should be redirected to the login page on its next action")
    public void theirActiveSessionShouldBeRedirectedToTheLoginPageOnItsNextAction() {
        otherSession.navigate("/en/dashboard/");
        assertThat(new LoginPage(otherSession).emailInput()).isVisible();
    }

    @When("I search users for that last name in lower case")
    public void iSearchUsersForThatLastNameInLowerCase() {
        page().search(thatUser.lastName().toLowerCase(Locale.ROOT));
    }

    @When("I search users for that user's email")
    public void iSearchUsersForThatUsersEmail() {
        page().search(thatUser.email());
    }

    @Then("that user should be the only user listed")
    public void thatUserShouldBeTheOnlyUserListed() {
        UsersPage users = page();
        assertThat(users.userCards()).hasCount(1);
        assertThat(users.userCard(thatUser.email())).isVisible();
    }

    @Then("no users should be listed")
    public void noUsersShouldBeListed() {
        assertThat(ctx.page()).hasURL(Pattern.compile(".*[?&]search=.*"));
        assertThat(page().userCards()).hasCount(0);
    }

    // ---- views ----

    @When("I switch the users list to card view")
    public void iSwitchTheUsersListToCardView() {
        page().switchToCardView();
    }

    @When("I switch the users list to list view")
    public void iSwitchTheUsersListToListView() {
        page().switchToListView();
    }

    @Then("the users should be shown as cards")
    public void theUsersShouldBeShownAsCards() {
        UsersPage users = page();
        assertThat(users.cardView()).isVisible();
        assertThat(users.listView()).isHidden();
    }

    @Then("the users should be shown as table rows")
    public void theUsersShouldBeShownAsTableRows() {
        UsersPage users = page();
        assertThat(users.listView()).isVisible();
        assertThat(users.cardView()).isHidden();
    }
}
