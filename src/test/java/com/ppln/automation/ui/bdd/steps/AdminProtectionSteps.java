package com.ppln.automation.ui.bdd.steps;

import com.microsoft.playwright.options.FormData;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.fixtures.AdminHttp;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.assertj.core.api.Assertions;

/**
 * Guards around the seeded Super Administrator (user id 1). The admin is hidden from the Users
 * list, so these send the requests the edit/delete controls would, over the logged-in admin session
 * with the page's CSRF token, and read the account back afterwards.
 *
 * <p>SAFETY: PUT /update_user/1 overwrites names, designation, phone, address, about, role and the
 * active flag from the form, so every request resends the admin's CURRENT values (read first).
 */
public class AdminProtectionSteps {

    private static final int ADMIN_ID = 1;
    private static final String ADMIN_PATH = "/en/users/update_user/" + ADMIN_ID;
    private static final String ADMINISTRATOR_ROLE = "1";

    private final UiScenarioContext ctx;

    public AdminProtectionSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("I send a request to delete the Super Administrator account")
    public void iSendARequestToDeleteTheAdmin() {
        SessionHttp.send(ctx, "DELETE", ADMIN_PATH, null);
    }

    @Then("the delete request should be rejected with {string}")
    public void theDeleteRequestShouldBeRejectedWith(String message) {
        HttpResult result = ctx.lastHttp();
        Assertions.assertThat(result.status()).isEqualTo(403);
        Assertions.assertThat(result.body()).contains(ctx.resolve(message));
    }

    @Then("the Super Administrator account should still exist")
    public void theAdminAccountShouldStillExist() {
        Assertions.assertThat(admin().email()).isEqualTo("admin@threatidr.com");
    }

    @When("I send a request to change the Super Administrator's role to a non-admin role")
    public void iSendARequestToChangeTheAdminRole() {
        // the "test" role (id 2) is a non-admin role; the guard must reject on role alone
        putAdmin("2");
    }

    /** The role field is blank (the edit form's select left unset); see {@link #putAdmin}. */
    @When("I send a request to update the Super Administrator with the role field blank")
    public void iSendARequestToUpdateTheAdminWithTheRoleFieldBlank() {
        putAdmin("");
    }

    /**
     * The admin's stored designation is NULL but the edit form requires one, so "Administrator"
     * stands in: without it validation stops at the designation and the role guard under test is
     * never reached. Every other field is the stored value. A PASS means the server refused the
     * request (nothing is written); only a broken guard could change the account.
     */
    private void putAdmin(String role) {
        AdminHttp.UserRecord current = admin();
        FormData form = FormData.create();
        AdminHttp.editForm(current).forEach(form::set);
        if (current.designation() == null || current.designation().isBlank()) {
            form.set("designation", "Administrator");
        }
        form.set("is_active_edit", "Yes");
        form.set("role", role);
        SessionHttp.send(
                ctx, "PUT", ADMIN_PATH, SessionHttp.withCsrf(ctx.page()).setMultipart(form));
    }

    @Then("the role-change request should be rejected with {string}")
    public void theRoleChangeShouldBeRejectedWith(String message) {
        HttpResult result = ctx.lastHttp();
        Assertions.assertThat(result.status()).isEqualTo(400);
        Assertions.assertThat(result.body()).contains(ctx.resolve(message));
    }

    @Then("the Super Administrator's role should still be Administrator")
    public void theAdminRoleShouldStillBeAdministrator() {
        Assertions.assertThat(admin().roleId()).hasToString(ADMINISTRATOR_ROLE);
    }

    private AdminHttp.UserRecord admin() {
        return ctx.admin().user(ADMIN_ID).orElseThrow();
    }
}
