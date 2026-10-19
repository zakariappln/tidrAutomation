package com.ppln.automation.ui.pages.profile;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.BasePage;
import io.qameta.allure.Step;
import java.nio.file.Path;
import java.util.Map;

/**
 * The logged-in user's own profile ({@code /<lang_code>/user/profile}), two tabs:
 *
 * <ul>
 *   <li>"User Profile": read-only details (Full Name / Email / About / Mobile / Address); unset
 *       fields render the literal text "None". "Edit" swaps in a form whose inputs carry WTForms
 *       constraints as HTML attributes (required, minlength, maxlength), so the browser blocks
 *       too-short or empty values before the PUT and truncates over-long ones. Its labels point at
 *       non-existent ids ({@code for="first_name_edit"}), so inputs are located by their
 *       human-written ids. Field errors from the server go to {@code #error_<field>_profile}.
 *   <li>"Update Password": current / new / confirm inputs (placeholders) and "Update". A success
 *       reloads the page without showing a message; errors go to {@code
 *       #error_password_update_<field>}.
 * </ul>
 */
public final class ProfilePage extends BasePage {

    /** Feature-file field name -> form field name (input id is {@code <name>_profile}). */
    private static final Map<String, String> FIELDS =
            Map.of(
                    "First Name", "first_name",
                    "Last Name", "last_name",
                    "Designation", "designation",
                    "Phone", "phone",
                    "Address", "address",
                    "About", "about");

    private final Locator fullNameLabel;
    private final Locator emailLabel;
    private final Locator detailsSection;
    private final Locator editButton;
    private final Locator editForm;
    private final Locator saveButton;
    private final Locator photoInput;
    private final Locator passwordTab;
    private final Locator currentPassword;
    private final Locator newPassword;
    private final Locator confirmPassword;
    private final Locator updatePasswordButton;

    public ProfilePage(Page page) {
        super(page);
        fullNameLabel = page.getByText("Full Name", new Page.GetByTextOptions().setExact(true));
        emailLabel = page.getByText("Email", new Page.GetByTextOptions().setExact(true));
        detailsSection = page.locator("#profile-section");
        editButton =
                detailsSection.getByRole(
                        AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName("Edit").setExact(true));
        editForm = page.locator("#profile_edit_form");
        saveButton =
                editForm.getByRole(
                        AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName("Save").setExact(true));
        photoInput = page.locator("#user_file_profile");
        passwordTab =
                page.getByRole(
                        AriaRole.TAB,
                        new Page.GetByRoleOptions().setName("Update Password").setExact(true));
        currentPassword = page.getByPlaceholder("Enter current password");
        newPassword = page.getByPlaceholder("Enter new password");
        confirmPassword =
                page.locator("#password-update-form").getByPlaceholder("Enter Confirm Password");
        updatePasswordButton =
                page.locator("#password-update-form")
                        .getByRole(
                                AriaRole.BUTTON,
                                new Locator.GetByRoleOptions().setName("Update").setExact(true));
    }

    @Override
    protected String path() {
        return "/en/user/profile";
    }

    public Locator fullNameLabel() {
        return fullNameLabel;
    }

    public Locator emailLabel() {
        return emailLabel;
    }

    /** The displayed value for {@code email} (exact text), e.g. the logged-in user's address. */
    public Locator valueShown(String value) {
        return page.getByText(value, new Page.GetByTextOptions().setExact(true)).first();
    }

    /** The read-only details card (name, designation, email, about, mobile, address). */
    public Locator detailsSection() {
        return detailsSection;
    }

    // ---- edit profile ----

    @Step("Open the profile edit form")
    public ProfilePage openEditForm() {
        editButton.click();
        return this;
    }

    /** Sets fields of the open edit form; an empty value clears the field. */
    @Step("Fill profile fields")
    public ProfilePage fill(Map<String, String> fields) {
        fields.forEach((field, value) -> input(field).fill(value));
        return this;
    }

    @Step("Choose profile photo {file}")
    public ProfilePage choosePhoto(Path file) {
        photoInput.setInputFiles(file);
        return this;
    }

    @Step("Save the profile")
    public ProfilePage save() {
        saveButton.click();
        return this;
    }

    public Locator input(String field) {
        return page.locator("#" + formName(field) + "_profile");
    }

    /** Server-side error element of a field. */
    public Locator error(String field) {
        return page.locator("#error_" + formName(field) + "_profile");
    }

    /**
     * What the user is shown for a bad field: the server's error element or, when the browser
     * blocked the submit, the input itself in its {@code :invalid} state (native message).
     */
    public Locator fieldProblem(String field) {
        return error(field)
                .filter(new Locator.FilterOptions().setVisible(true))
                .or(page.locator("#" + formName(field) + "_profile:invalid"))
                .first();
    }

    /** Inputs the browser currently considers invalid (constraint validation blocks the submit). */
    public Locator invalidInputs() {
        return editForm.locator("input:invalid");
    }

    private static String formName(String field) {
        String name = FIELDS.get(field);
        if (name == null) {
            throw new IllegalArgumentException(
                    "Unknown profile field '%s'; known: %s".formatted(field, FIELDS.keySet()));
        }
        return name;
    }

    // ---- update password ----

    @Step("Change password")
    public ProfilePage changePassword(String current, String replacement) {
        passwordTab.click();
        currentPassword.fill(current);
        newPassword.fill(replacement);
        confirmPassword.fill(replacement);
        updatePasswordButton.click();
        return this;
    }

    /** Visible password-form errors. */
    public Locator passwordErrors() {
        return page.locator("#password-update-form .invalid-feedback")
                .filter(new Locator.FilterOptions().setVisible(true));
    }
}
