@ui @users @roles @serial-users
Feature: Role management
  An administrator defines custom roles with a specific set of permissions, used to scope what
  each user can see and do in ThreatIDR.

  # Confirmed live 2026-10-17: the role form offers exactly 15 action permissions, labelled e.g.
  # "Read Reports", "Export Reports", "Update Settings", "Create/Update/Delete Users",
  # "Create/Update/Delete Roles", "Create/Update/Delete Alert Types" (sic: "Update ALert Types"),
  # "Create/Update/Delete Email Templates". There are no page-level permissions such as
  # "Dashboard" or "Threat Detection"; a module is unlocked by holding any action in it.
  #
  # Source facts (views/user/forms.py:124-173): name required (DB String(255), unique); zero
  # permissions -> "At least one permission must be selected."; duplicate on create -> "A role with
  # this name already exists" (exact case). The duplicate check is skipped on EDIT, so renaming to an
  # existing name raises a database IntegrityError (500): tagged @known-defect below (the role PUT
  # commits without a try/except, views/user/views.py:560-620).

  Background:
    Given I am logged in as "admin"
    And I am on the roles tab of the users page

  @smoke @positive
  Scenario: Creating a role with at least one permission succeeds
    When I create a role named "QA Role {uniqueString}" with the "Read Reports" permission
    Then the role "QA Role {uniqueString}" should appear in the roles list

  # Known defect, confirmed live 2026-10-19: the server rejects the duplicate with "A role with this
  # name already exists" (errors.name), but users.js saveRole() only toggles d-none on
  # #error_role_name, an .invalid-feedback element that is display:none by default, so the reason
  # is never shown; the user only sees the generic toast "Failed to create Role".
  @regression @negative @known-defect
  Scenario: BUG - creating a role with a name that already exists shows why it was rejected
    Given a role named "QA Dup {uniqueString}" already exists
    When I create a role named "QA Dup {uniqueString}" with the "Read Reports" permission
    Then I should see the role error "A role with this name already exists"

  @regression @negative
  Scenario: Creating a role with zero permissions selected is rejected
    When I create a role named "QA Empty Role {uniqueString}" with no permissions selected
    Then I should see the role error "At least one permission must be selected."

  @regression @negative @security @known-defect
  Scenario: BUG - renaming a role to an existing role's name is rejected on edit too
    Given roles named "QA Alpha {uniqueString}" and "QA Beta {uniqueString}" both exist
    When I rename the "QA Beta {uniqueString}" role to "QA Alpha {uniqueString}"
    Then I should see the role error "A role with this name already exists"
    And the request should not cause a server error

  # The least-privilege sidebar/denial check lives in authorization/role-based-access.feature.
