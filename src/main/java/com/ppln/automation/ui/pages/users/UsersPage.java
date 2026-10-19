package com.ppln.automation.ui.pages.users;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.BasePage;
import io.qameta.allure.Step;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/**
 * Users + Roles ({@code /<lang_code>/users/}, two tabs). Confirmed live 2026-10-17/19:
 *
 * <ul>
 *   <li>Create-user inputs have no accessible labels usable here (the create and edit forms reuse
 *       label texts), so they are located by placeholder; "Save" is an {@code <a>} with no href (no
 *       accessible role), so it is located by exact text. Field errors are the {@code
 *       #error_create_user_<field>} elements the page script fills.
 *   <li>Users render twice in the DOM: a card view ({@code #cardView}) and a list view ({@code
 *       #listView}); only one is shown, toggled by the "Card View" / "List View" icons.
 *   <li>The phone input is an intl-tel-input widget with a separate dial code ("+880" for en): the
 *       script submits {@code +<dial code> <typed value>}.
 *   <li>The role form offers 15 action permissions as checkboxes with real labels ("Read Reports",
 *       ...); "Create" is a real submit button; editing reuses the form with "Update".
 *   <li>Both deletes confirm with a dialog whose button is "Yes, Delete it".
 * </ul>
 */
public final class UsersPage extends BasePage {

    /** Feature-file field name -> placeholder of the create-user input. */
    private static final Map<String, String> USER_FIELDS =
            Map.of(
                    "First Name", "Enter First Name",
                    "Last Name", "Enter Last Name",
                    "Email", "Enter Email",
                    "Password", "Enter Password",
                    "Confirm Password", "Enter Confirm Password",
                    "Title", "Enter Title");

    /** Feature-file field name -> suffix of the create form's error element id. */
    private static final Map<String, String> USER_ERRORS =
            Map.of(
                    "First Name", "first_name",
                    "Last Name", "last_name",
                    "Email", "email",
                    "Password", "password",
                    "Confirm Password", "confirm_password",
                    "Title", "designation",
                    "Phone", "phone");

    private final Locator createUserLink;
    private final Locator saveUser;
    private final Locator phoneInput;
    private final Locator photoInput;
    private final Locator searchInput;
    private final Locator cardView;
    private final Locator listView;
    private final Locator cardViewToggle;
    private final Locator listViewToggle;
    private final Locator editPhoneInput;
    private final Locator editEmailInput;
    private final Locator updateUserButton;
    private final Locator rolesTab;
    private final Locator addNewRoleLink;
    private final Locator roleNameInput;
    private final Locator createRoleButton;
    private final Locator updateRoleButton;
    private final Locator confirmDeleteButton;

    public UsersPage(Page page) {
        super(page);
        createUserLink =
                page.getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("Create").setExact(true));
        saveUser = page.getByText("Save", new Page.GetByTextOptions().setExact(true));
        // human-written ids: the phone widget has no placeholder, the photo input is hidden
        phoneInput = page.locator("#create_user_phone");
        photoInput = page.locator("#create_user_file");
        searchInput = page.getByPlaceholder("Search Users");
        cardView = page.locator("#cardView");
        listView = page.locator("#listView");
        cardViewToggle = page.locator("#ctweak");
        listViewToggle = page.locator("#ltweak");
        editPhoneInput = page.locator("#phone_edit");
        editEmailInput = page.locator("#email_edit");
        updateUserButton =
                page.locator("#user-edit")
                        .getByRole(
                                AriaRole.BUTTON,
                                new Locator.GetByRoleOptions().setName("Update").setExact(true));
        rolesTab =
                page.getByRole(
                        AriaRole.TAB, new Page.GetByRoleOptions().setName("Roles").setExact(true));
        addNewRoleLink =
                page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Add New Role"));
        roleNameInput = page.getByPlaceholder("A name for role");
        createRoleButton =
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Create").setExact(true));
        updateRoleButton = page.locator("#role-update-button");
        confirmDeleteButton =
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Yes, Delete it"));
    }

    @Override
    protected String path() {
        return "/en/users/";
    }

    @Step("Open the users list filtered by {search}")
    public UsersPage openSearch(String search) {
        page.navigate(path() + "?search=" + URLEncoder.encode(search, StandardCharsets.UTF_8));
        return this;
    }

    // ---- create user ----

    /**
     * Fills the create-user form and clicks Save. Fields not in the map are left empty.
     *
     * @param fields feature-file field name -> already-resolved value ("Phone" included)
     * @param photo file for the avatar input, or {@code null}
     */
    @Step("Create user")
    public UsersPage createUser(Map<String, String> fields, Path photo) {
        createUserLink.click();
        fields.forEach(
                (field, value) -> {
                    if ("Phone".equals(field)) {
                        phoneInput.fill(value);
                        return;
                    }
                    String placeholder = USER_FIELDS.get(field);
                    if (placeholder == null) {
                        throw new IllegalArgumentException(
                                "Unknown user field '%s'; known: %s, Phone"
                                        .formatted(field, USER_FIELDS.keySet()));
                    }
                    page.getByPlaceholder(placeholder).fill(value);
                });
        if (photo != null) {
            photoInput.setInputFiles(photo);
        }
        saveUser.click();
        return this;
    }

    /** The create form's error element for a feature-file field name. */
    public Locator createUserError(String field) {
        String suffix = USER_ERRORS.get(field);
        if (suffix == null) {
            throw new IllegalArgumentException(
                    "Unknown user field '%s'; known: %s".formatted(field, USER_ERRORS.keySet()));
        }
        return page.locator("#error_create_user_" + suffix);
    }

    /** Every visible field error of the create form. */
    public Locator visibleCreateUserErrors() {
        return page.locator("#user-create .invalid-feedback")
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    // ---- list ----

    /** The card for the user with {@code email} (cards carry the email as text). */
    public Locator userCard(String email) {
        return cardView.locator("> div").filter(new Locator.FilterOptions().setHasText(email));
    }

    public Locator userCards() {
        return cardView.locator("> div[id^='pipeline-user-lists-']");
    }

    public Locator cardView() {
        return cardView;
    }

    public Locator listView() {
        return listView;
    }

    @Step("Search users for {text}")
    public UsersPage search(String text) {
        searchInput.fill(text);
        searchInput.press("Enter");
        return this;
    }

    @Step("Switch to card view")
    public UsersPage switchToCardView() {
        cardViewToggle.click();
        return this;
    }

    @Step("Switch to list view")
    public UsersPage switchToListView() {
        listViewToggle.click();
        return this;
    }

    @Step("Remove user {email}")
    public UsersPage removeUser(String email) {
        userCard(email).getByText("Remove", new Locator.GetByTextOptions().setExact(true)).click();
        confirmDeleteButton.click();
        return this;
    }

    // ---- edit user ----

    /** Opens the edit form from the user's card; the form is filled asynchronously. */
    @Step("Open the edit form of {email}")
    public UsersPage openEdit(String email) {
        userCard(email).getByText("Edit", new Locator.GetByTextOptions().setExact(true)).click();
        return this;
    }

    /** Email shown in the edit form; filled once the user's data has loaded. */
    public Locator editEmailInput() {
        return editEmailInput;
    }

    public Locator editPhoneInput() {
        return editPhoneInput;
    }

    /** The edit form's error element for a feature-file field name ("Phone" -> phone). */
    public Locator editUserError(String field) {
        String suffix = USER_ERRORS.get(field);
        if (suffix == null) {
            throw new IllegalArgumentException(
                    "Unknown user field '%s'; known: %s".formatted(field, USER_ERRORS.keySet()));
        }
        return page.locator("#error_" + suffix + "_edit");
    }

    @Step("Set the phone number to {phone} and update")
    public UsersPage updatePhone(String phone) {
        editPhoneInput.fill(phone);
        updateUserButton.click();
        return this;
    }

    // ---- roles ----

    @Step("Open the Roles tab")
    public UsersPage openRolesTab() {
        rolesTab.click();
        return this;
    }

    @Step("Create role {name} with permission {permission}")
    public UsersPage createRole(String name, String permission) {
        addNewRoleLink.click();
        roleNameInput.fill(name);
        if (permission != null) {
            page.getByLabel(permission, new Page.GetByLabelOptions().setExact(true)).check();
        }
        createRoleButton.click();
        return this;
    }

    public Locator roleRow(String name) {
        return page.locator("#role-list tbody tr")
                .filter(
                        new Locator.FilterOptions()
                                .setHas(
                                        page.getByRole(
                                                AriaRole.CELL,
                                                new Page.GetByRoleOptions()
                                                        .setName(name)
                                                        .setExact(true))));
    }

    /** Opens the role form in edit mode; the name is filled asynchronously. */
    @Step("Edit role {name}")
    public UsersPage editRole(String name) {
        roleRow(name)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Edit"))
                .click();
        return this;
    }

    public Locator roleNameInput() {
        return roleNameInput;
    }

    @Step("Rename the role to {newName} and update")
    public UsersPage renameRoleTo(String newName) {
        roleNameInput.fill(newName);
        updateRoleButton.click();
        return this;
    }

    /** A visible role-form error (name or permissions) containing {@code text}. */
    public Locator roleError(String text) {
        return page.locator("#role-form [id^='error_role_']")
                .filter(new Locator.FilterOptions().setHasText(text));
    }

    @Step("Delete role {name}")
    public UsersPage deleteRole(String name) {
        roleRow(name)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete"))
                .click();
        confirmDeleteButton.click();
        return this;
    }
}
