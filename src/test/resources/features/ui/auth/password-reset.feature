@ui @auth @password-reset
Feature: Password reset
  A user who forgot their password requests a reset link and sets a new password from it.

  # OUT OF SCOPE (decision 2026-10-18): forgot password cannot be tested here (no mail relay, no
  # way to read the reset token). Kept as specification only; tagged @out-of-scope.
  # Source facts for whoever picks this up: token is unsigned base64(zlib("id&&email&&seconds")),
  # valid 300 s and reusable (models/user.py:62-74, core/utils.py:135-158).
  #
  # ASSUMPTIONS (confirm or correct):
  #  - the confirmation message is identical whether the email is registered or not (no account
  #    enumeration) - not yet reproduced live, flagged as a thing to deliberately verify
  #  - the password complexity error text per missing character class is not confirmed
  #  - there is no live mail relay in this environment; the reset link/token must be read back via
  #    a test hook or a DB/API lookup rather than a real inbox - bdd-step-writer should confirm how
  @regression @positive @out-of-scope @wip
  Scenario: Requesting a reset link for a registered email shows a generic confirmation
    Given I open the login page
    When I request a password reset for "admin@threatidr.com"
    Then I should see a generic password-reset confirmation message
  @regression @negative @security @out-of-scope @wip
  Scenario: Requesting a reset link for an unregistered email shows the same generic confirmation
    Given I open the login page
    When I request a password reset for "{uniqueEmail}"
    Then I should see a generic password-reset confirmation message
  @regression @negative @out-of-scope @wip
  Scenario Outline: Rejects a new password that fails the complexity rule
    Given I have a valid password-reset link
    When I set my new password to "<new password>"
    Then I should see a password complexity error

    Examples:
      | new password | missing rule        |
      | abcdefg1!     | uppercase letter    |
      | ABCDEFG1!     | lowercase letter    |
      | Abcdefgh!     | digit               |
      | Abcdefg1      | special character   |
      | Ab1!xyz       | minimum length (8)  |
  @regression @positive @out-of-scope @serial-auth @wip
  Scenario: Accepts a new password that meets the complexity rule
    Given I have a valid password-reset link
    When I set my new password to "Ab1!xyzz"
    Then my password should be changed successfully
    And I should be able to log in with the new password
  @regression @negative @security @out-of-scope @wip
  Scenario: Rejects a reset link that has already been used
    Given I have a password-reset link that was already used once
    When I set my new password to "Ab1!xyzz"
    Then I should see an invalid-or-expired-token error
