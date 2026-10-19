@ui @auth @login
Feature: Login
  # @serial-auth (see SerialAuthHooks) marks the scenarios that submit the real login form: they
  # share one IP-keyed 3/minute rate-limit bucket with each other and with the one-time login
  # StorageStateManager performs for every OTHER feature's Background, so they run one at a time,
  # three per window. The two that deliberately exhaust the limit add @login-window and get a clear
  # window to themselves. The anonymous-redirect rows never log in and are not tagged.
  A user signs in to the ThreatIDR web app.

  # Confirmed live against ThreatIDR v1.5.1 (tidr-deploy-v1) on 2026-10-17 via curl + claude-in-chrome:
  # the 4th login POST within 60s returns HTTP 429 with a "Rate Limit Exceeded" page (h2), not an
  # inline banner. "admin" resolves to AUTH_ADMIN_USERNAME/PASSWORD, falling back to
  # AUTH_USERNAME/PASSWORD (see config/staging.local.properties).
  #
  # SECURITY DEFECT CONFIRMED LIVE: the error text differs for unknown vs known emails, which lets
  # an attacker enumerate accounts. The "BUG -" scenario asserts the correct (identical) message and
  # is tagged @known-defect, so it is excluded from the default run until fixed.

  # Inactive accounts (source: views/home/forms.py:23-41): "User not activated" is shown only when
  # the password is correct, and it is attached to the EMAIL field, so flash_errors renders it as
  # "Email - User not activated" (core/utils.py:39-43); a wrong password still says
  # "Password - Wrong credentials". The inactive account is created as admin and deactivated
  # through Edit User (is_active_edit = No).

  @smoke @positive @serial-auth
  Scenario: Shows the dashboard after a valid login
    Given I open the login page
    When I log in as "admin"
    Then I should be on the dashboard

  @regression @negative @serial-auth
  Scenario: Shows an error for a registered email with the wrong password
    Given I open the login page
    When I log in with username "admin@threatidr.com" and password "wrong-password"
    Then I should see the login error "Password - Wrong credentials"

  # Known defect (views/home/forms.py:31,35 + core/utils.py:43): unknown email shows
  # "Email - Wrong credentials", known email shows "Password - Wrong credentials".
  @regression @negative @security @serial-auth @known-defect
  Scenario: BUG - an unregistered email gets the same error as a wrong password
    Given I open the login page
    When I log in with username "{uniqueEmail}" and password "wrong-password"
    Then I should see the login error "Password - Wrong credentials"

  # Empty fields are blocked client-side (required attributes on both inputs), so no POST is sent
  # and no rate-limit slot is spent.
  @regression @negative
  Scenario Outline: Empty email or password is rejected before submitting
    Given I open the login page
    When I log in with username "<email>" and password "<password>"
    Then the login form should not be submitted
    And the "<empty field>" field should be flagged as required

    Examples:
      | email               | password      | empty field |
      |                     | wrong-pass    | Email       |
      | admin@threatidr.com |               | Password    |

  @regression @negative @security @serial-auth @serial-users
  Scenario: An inactive user sees "User not activated" after the correct password
    Given an inactive user exists
    And I open the login page
    When I log in as that inactive user
    Then I should see the login error "Email - User not activated"
    And I should be on the login page

  @regression @negative @security @serial-auth @login-window
  Scenario: Blocks a 4th login attempt within 60 seconds
    Given I open the login page
    When I attempt to log in 3 times in a row with username "{uniqueEmail}" and password "wrong-password"
    And I log in with username "{uniqueEmail}" and password "wrong-password"
    Then I should see the rate-limit-exceeded page

  @regression @positive @slow @serial-auth @login-window
  Scenario: Allows login again after the rate-limit window passes
    Given I open the login page
    And I have just been rate-limited after 3 failed login attempts
    When I wait for the rate-limit window to pass
    And I log in as "admin"
    Then I should be on the dashboard

  @regression @negative @serial-auth
  Scenario: Logging out ends the session
    Given I open the login page
    When I log in as "admin"
    And I log out
    And I navigate back in the browser history
    Then I should be on the login page

  @regression @negative @security
  Scenario Outline: Redirects an anonymous visitor to the login page
    When I navigate directly to "<protected page>" without logging in
    Then I should be on the login page

    Examples:
      | protected page            |
      | /en/dashboard/            |
      | /en/users/                |
      | /en/settings/             |
      | /en/dga_domains/          |
