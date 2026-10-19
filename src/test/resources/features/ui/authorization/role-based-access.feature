@ui @authorization @security
Feature: Role-based access control
  Access to each module is controlled by permission strings checked in code (has_permission /
  has_module_permission), not by a fixed role name.

  # Source facts (docs/threatidr-source-facts.md sections 0, 3, 4, 8, 10):
  #  - has_module_permission(X) is true when the user holds ANY "X-*" action. The role form offers
  #    report-*, setting-*, user-*, role-*, alerttype-*, emailtemplate-* only, so "dga" and "backup"
  #    can never be granted to a custom role.
  #  - page denial renders 401.html with HTTP 200 (not a redirect, not a 403).
  #  - guards: Users page = module user OR role; Blocked Domains list = module user; Allowed
  #    Domains list = "user-create" (asymmetric with Blocked); Settings = module setting OR
  #    alerttype OR emailtemplate; Reports page = module report; report data = "report-read".
  #  - sidebar (templates/sidebar.html): Blocked/Allowed Domains need module user, Reports module
  #    report, Users module user or role, Settings module setting.
  #  - Blocked Domains renders the Delete button only with "user-delete"
  #    (templates/blocked_domain/index.html:80); DGA denial is covered in dga-domains.feature.
  #
  # Limited users come from the shared fixture (see features/ui/README.md): one role + one user per
  # permission set per run, logged in once through the rate-limit guard, removed at the end.

  @regression @negative @security
  Scenario Outline: A user with only "Read Reports" is denied every other module
    Given I am logged in as a user with only the "Read Reports" permission
    When I navigate directly to "<page>"
    Then I should see an access-denied page

    Examples:
      | page                 |
      | /en/settings/        |
      | /en/blocked_domains/ |
      | /en/allowed_domains/ |
      | /en/dga_domains/     |

  # Known defect, confirmed live 2026-10-19 (views/user/views.py:36-91): the Users page renders
  # 401.html only for POST; a GET without any user-* or role-* permission falls off the end of the
  # view and the server answers HTTP 500 instead of the access-denied page.
  @regression @negative @security @known-defect
  Scenario: BUG - a user with only "Read Reports" is denied the Users page without a server error
    Given I am logged in as a user with only the "Read Reports" permission
    When I navigate directly to "/en/users/"
    Then the page should load without a server error
    And I should see an access-denied page

  @regression @positive
  Scenario: A user with only "Read Reports" can reach Reports
    Given I am logged in as a user with only the "Read Reports" permission
    When I navigate directly to "/en/reports/"
    Then the page should load without a server error
    And I should not see an access-denied page

  @regression @positive @security
  Scenario: A user with only "Read Reports" sees only the permitted modules in the sidebar
    Given I am logged in as a user with only the "Read Reports" permission
    Then the sidebar should show the menu label "Reports"
    And the sidebar should not show the menu label "Users"
    And the sidebar should not show the menu label "Settings"

  @regression @positive @security
  Scenario: A user with only "Update Users" sees Blocked Domains but not Allowed Domains
    Given I am logged in as a user with only the "Update Users" permission
    When I navigate directly to "/en/blocked_domains/"
    Then the page should load without a server error
    And I should not see an access-denied page
    When I navigate directly to "/en/allowed_domains/"
    Then I should see an access-denied page

  @regression @positive
  Scenario: A user with only "Create Users" can reach Allowed Domains
    Given I am logged in as a user with only the "Create Users" permission
    When I navigate directly to "/en/allowed_domains/"
    Then the page should load without a server error
    And I should not see an access-denied page

  @regression @negative @security
  Scenario: A user without delete permission is not offered a delete control for blocked domains
    Given "qa-rbac-{uniqueString}.example" is already a blocked domain
    And I am logged in as a user with only the "Update Users" permission
    And I am on the blocked domains page
    Then "qa-rbac-{uniqueString}.example" should appear in the blocked domains list
    And the blocked domain "qa-rbac-{uniqueString}.example" should have no delete control

  # Known defect: an unauthorized delete returns jsonify({'errors':'Unauthorized'}, 401), which
  # Flask serialises as the body [{"errors":"Unauthorized"},401] with HTTP 200
  # (views/blocked_domain/views.py:219); the page JS then shows "Item deleted successfully" for any
  # 200 (assets/js/scripts/blockedDomains.js). The request is sent over the limited user's session
  # with the page's X-CSRFToken, because the UI hides the button.
  @regression @negative @security @known-defect
  Scenario: BUG - a delete request without delete permission is refused with HTTP 401
    Given "qa-rbac-del-{uniqueString}.example" is already a blocked domain
    And I am logged in as a user with only the "Update Users" permission
    And I am on the blocked domains page
    When I send a request to delete the blocked domain "qa-rbac-del-{uniqueString}.example"
    Then the delete request should be answered with HTTP status 401
    When I reload the blocked domains page
    Then "qa-rbac-del-{uniqueString}.example" should appear in the blocked domains list
