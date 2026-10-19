package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.bdd.support.UploadFiles;
import com.ppln.automation.ui.fixtures.AdminHttp;
import com.ppln.automation.ui.fixtures.FixtureUsers;
import com.ppln.automation.ui.pages.profile.ProfilePage;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;

/**
 * Own profile and password. Edits only ever run on a fixture or disposable account (never the
 * admin, whose NULL designation could not be restored): the account's stored values are read before
 * the change and restored over the admin API when the save went through.
 */
public class ProfileSteps {

    private static final String PROFILE_GLOB = "**/user/update_profile";
    private static final String PASSWORD_GLOB = "**/user/update_password";

    private final UiScenarioContext ctx;
    private Map<String, String> lastTyped = Map.of();
    private Optional<HttpResult> lastSave = Optional.empty();

    public ProfileSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    private ProfilePage page() {
        return new ProfilePage(ctx.page());
    }

    @Given("I am on my profile page")
    public void iAmOnMyProfilePage() {
        new ProfilePage(ctx.page()).open();
    }

    @Then("I should see my current profile details")
    public void iShouldSeeMyCurrentProfileDetails() {
        ProfilePage page = page();
        String email = ctx.get(AuthSteps.CURRENT_USERNAME);
        assertThat(page.fullNameLabel()).isVisible();
        assertThat(page.emailLabel()).isVisible();
        assertThat(page.valueShown(email)).isVisible();
    }

    // ---- profile fields ----

    /** Table {@code | field | value |}. */
    @When("I update my profile with:")
    public void iUpdateMyProfileWith(DataTable table) {
        Map<String, String> fields = new LinkedHashMap<>();
        table.asMaps().forEach(row -> fields.put(row.get("field"), ctx.resolve(row.get("value"))));
        saveProfile(fields, null);
    }

    @Then("my profile should show the updated values after reloading")
    public void myProfileShouldShowTheUpdatedValuesAfterReloading() {
        assertSaved();
        ProfilePage profile = page().open();
        lastTyped
                .values()
                .forEach(value -> assertThat(profile.detailsSection()).containsText(value));
    }

    @When("I update my profile {string} with a {int}-character value")
    public void iUpdateMyProfileWithACharacterValue(String field, int length) {
        String name = ctx.resolve(field);
        String value =
                "Phone".equals(name) ? "1".repeat(length) : FixtureUsers.randomLetters(length);
        saveProfile(Map.of(name, value), null);
    }

    /**
     * "accepted": the server answered with its success message and stored exactly the typed value.
     * "rejected": the typed value was not stored - the browser blocked the submit
     * (required/minlength), the server answered with an error, or the browser truncated it
     * (maxlength).
     */
    @Then("the profile update should be {string}")
    public void theProfileUpdateShouldBe(String expected) {
        String field = lastTyped.keySet().iterator().next();
        String typed = lastTyped.get(field);
        switch (ctx.resolve(expected).toLowerCase(Locale.ROOT)) {
            case "accepted" -> {
                assertSaved();
                Assertions.assertThat(storedValue(field)).isEqualTo(typed);
            }
            case "rejected" -> {
                if (lastSave.isEmpty()) {
                    assertThat(page().invalidInputs().first()).isVisible();
                } else {
                    Assertions.assertThat(storedValue(field))
                            .as("stored %s after a rejected update", field)
                            .isNotEqualTo(typed);
                }
            }
            default ->
                    throw new IllegalArgumentException(
                            "Expected accepted or rejected: " + expected);
        }
    }

    @When("I update my profile leaving {string} empty")
    public void iUpdateMyProfileLeavingEmpty(String field) {
        saveProfile(Map.of(ctx.resolve(field), ""), null);
    }

    @When("I upload {string} as my profile photo")
    public void iUploadAsMyProfilePhoto(String file) {
        saveProfile(Map.of(), UploadFiles.get(ctx.resolve(file)));
    }

    @Then("the photo upload should be {string}")
    public void thePhotoUploadShouldBe(String expected) {
        switch (ctx.resolve(expected).toLowerCase(Locale.ROOT)) {
            case "accepted" -> assertSaved();
            case "rejected" -> {
                Assertions.assertThat(lastSave).as("the profile request was sent").isPresent();
                Assertions.assertThat(lastSave.get().status())
                        .as("a rejection is an answer, not a crash: %s", lastSave.get().body())
                        .isLessThan(500);
                JsonNode body = CapturedCall.json(lastSave.get());
                Assertions.assertThat(body.path("message").asText())
                        .as("HTTP %d: %s", lastSave.get().status(), lastSave.get().body())
                        .isNotEqualTo("Profile updated successfully.");
            }
            default ->
                    throw new IllegalArgumentException(
                            "Expected accepted or rejected: " + expected);
        }
    }

    /**
     * Opens the edit form, applies {@code fields} (and a photo), saves and captures the PUT, or
     * notes that the browser refused to send it. A save that went through is undone afterwards.
     */
    private void saveProfile(Map<String, String> fields, Path photo) {
        int userId = currentNonAdminUserId();
        AdminHttp.UserRecord original = ctx.admin().user(userId).orElseThrow();
        lastTyped = fields;
        ProfilePage profile = page().openEditForm();
        profile.fill(fields);
        if (photo != null) {
            profile.choosePhoto(photo);
        }
        try (CapturedCall call = CapturedCall.of(ctx, "PUT", PROFILE_GLOB)) {
            profile.save();
            lastSave = call.await(() -> page().invalidInputs().count() > 0);
        }
        ctx.useFieldErrors(field -> new ProfilePage(ctx.page()).fieldProblem(field));
        lastSave.filter(r -> CapturedCall.json(r).path("status").asText().equals("Ok"))
                .ifPresent(
                        r ->
                                ctx.onCleanup(
                                        () ->
                                                ctx.admin()
                                                        .updateUser(
                                                                userId,
                                                                AdminHttp.editForm(original))));
    }

    private void assertSaved() {
        Assertions.assertThat(lastSave).as("the profile update request was sent").isPresent();
        HttpResult result = lastSave.get();
        Assertions.assertThat(CapturedCall.json(result).path("message").asText())
                .as("update_profile answered HTTP %d: %s", result.status(), result.body())
                .isEqualTo("Profile updated successfully.");
    }

    private String storedValue(String field) {
        AdminHttp.UserRecord u = ctx.admin().user(currentNonAdminUserId()).orElseThrow();
        String value =
                switch (field) {
                    case "First Name" -> u.firstName();
                    case "Last Name" -> u.lastName();
                    case "Designation" -> u.designation();
                    case "Phone" -> u.phone();
                    case "Address" -> u.address();
                    case "About" -> u.about();
                    default -> throw new IllegalArgumentException("Unknown profile field " + field);
                };
        return value == null ? "" : value;
    }

    /** Profile edits never run on the admin (see the feature's SAFETY note). */
    private int currentNonAdminUserId() {
        if (!ctx.has(FixtureUserSteps.CURRENT_USER_ID)) {
            throw new IllegalStateException(
                    "Profile edits must run as a fixture or disposable user, not "
                            + ctx.get(AuthSteps.CURRENT_USERNAME));
        }
        return Integer.parseInt(ctx.get(FixtureUserSteps.CURRENT_USER_ID));
    }

    // ---- password ----

    @When("I change my password from {string} to {string}")
    public void iChangeMyPasswordFromTo(String current, String replacement) {
        changePassword(ctx.resolve(current), ctx.resolve(replacement));
    }

    @When("I change my password to {string}")
    public void iChangeMyPasswordTo(String replacement) {
        changePassword(ctx.get(FixtureUserSteps.CURRENT_PASSWORD), ctx.resolve(replacement));
    }

    @When("I change my password to my current password")
    public void iChangeMyPasswordToMyCurrentPassword() {
        String current = ctx.get(FixtureUserSteps.CURRENT_PASSWORD);
        changePassword(current, current);
    }

    private void changePassword(String current, String replacement) {
        if (!ctx.has(FixtureUserSteps.CURRENT_PASSWORD)) {
            throw new IllegalStateException("Password changes run only as a disposable user");
        }
        try (CapturedCall call = CapturedCall.of(ctx, "PUT", PASSWORD_GLOB)) {
            page().changePassword(current, replacement);
            call.await();
        }
    }

    /**
     * Errors are shown under the field; a success reloads the page without displaying anything, so
     * the server's success message is what the user's action produced.
     */
    @Then("I should see the password message {string}")
    public void iShouldSeeThePasswordMessage(String message) {
        String expected = ctx.resolve(message);
        HttpResult result = ctx.lastHttp();
        if (CapturedCall.json(result).has("errors")) {
            assertThat(
                            page().passwordErrors()
                                    .filter(
                                            new com.microsoft.playwright.Locator.FilterOptions()
                                                    .setHasText(expected)))
                    .hasCount(1);
        } else {
            Assertions.assertThat(CapturedCall.json(result).path("message").asText())
                    .as("update_password answered HTTP %d: %s", result.status(), result.body())
                    .isEqualTo(expected);
        }
    }

    @Then("the password change should be {string}")
    public void thePasswordChangeShouldBe(String expected) {
        HttpResult result = ctx.lastHttp();
        JsonNode body = CapturedCall.json(result);
        switch (ctx.resolve(expected).toLowerCase(Locale.ROOT)) {
            case "accepted" ->
                    Assertions.assertThat(body.path("message").asText())
                            .as("HTTP %d: %s", result.status(), result.body())
                            .isEqualTo("Password updated successfully.");
            case "rejected" -> {
                Assertions.assertThat(body.has("errors"))
                        .as("HTTP %d: %s", result.status(), result.body())
                        .isTrue();
                assertThat(page().passwordErrors().first()).isVisible();
            }
            default ->
                    throw new IllegalArgumentException(
                            "Expected accepted or rejected: " + expected);
        }
    }
}
