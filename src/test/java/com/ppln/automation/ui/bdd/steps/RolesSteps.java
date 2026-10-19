package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import com.ppln.automation.ui.fixtures.PermissionCatalog;
import com.ppln.automation.ui.pages.users.UsersPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import org.assertj.core.api.Assertions;

/**
 * Roles tab. Every role this scenario creates (through the UI or as a precondition) is deleted over
 * the admin API in cleanup, looked up by name, so a rejected create never deletes someone else's
 * role.
 */
public class RolesSteps {

    private final UiScenarioContext ctx;

    public RolesSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    private UsersPage page() {
        return new UsersPage(ctx.page());
    }

    @Given("I am on the roles tab of the users page")
    public void iAmOnTheRolesTab() {
        new UsersPage(ctx.page()).<UsersPage>open().openRolesTab();
    }

    @When("I create a role named {string} with the {string} permission")
    public void iCreateARoleWithPermission(String name, String permission) {
        createRole(ctx.resolve(name), ctx.resolve(permission));
    }

    @When("I create a role named {string} with no permissions selected")
    public void iCreateARoleNamedWithNoPermissionsSelected(String name) {
        createRole(ctx.resolve(name), null);
    }

    private void createRole(String name, String permissionLabel) {
        boolean existedBefore = ctx.admin().roleId(name).isPresent();
        try (CapturedCall call = CapturedCall.of(ctx, "POST", "**/users/role")) {
            page().createRole(name, permissionLabel);
            call.await();
        }
        if (!existedBefore) {
            ctx.onCleanup(() -> ctx.admin().roleId(name).ifPresent(ctx.admin()::deleteRole));
        }
    }

    @Then("the role {string} should appear in the roles list")
    public void theRoleShouldAppearInTheRolesList(String name) {
        HttpResult created = ctx.lastHttp();
        Assertions.assertThat(CapturedCall.json(created).path("message").asText())
                .as("create role answered HTTP %d: %s", created.status(), created.body())
                .isEqualTo("Role created successfully.");
        assertThat(page().roleRow(ctx.resolve(name))).isVisible();
    }

    @Then("I should see the role error {string}")
    public void iShouldSeeTheRoleError(String message) {
        assertThat(page().roleError(ctx.resolve(message))).isVisible();
    }

    @Given("a role named {string} already exists")
    public void aRoleNamedAlreadyExists(String name) {
        roleExists(ctx.resolve(name));
    }

    @Given("roles named {string} and {string} both exist")
    public void rolesNamedAndBothExist(String first, String second) {
        roleExists(ctx.resolve(first));
        roleExists(ctx.resolve(second));
        page().<UsersPage>open().openRolesTab(); // show them
    }

    private void roleExists(String name) {
        int id = ctx.admin().ensureRole(name, List.of(PermissionCatalog.keyFor("Read Reports")));
        ctx.onCleanup(() -> ctx.admin().deleteRole(id));
    }

    @When("I rename the {string} role to {string}")
    public void iRenameTheRoleTo(String current, String newName) {
        UsersPage users = page().editRole(ctx.resolve(current));
        assertThat(users.roleNameInput()).hasValue(ctx.resolve(current)); // form data loaded
        try (CapturedCall call = CapturedCall.of(ctx, "PUT", "**/users/role/*")) {
            users.renameRoleTo(ctx.resolve(newName));
            call.await();
        }
    }
}
