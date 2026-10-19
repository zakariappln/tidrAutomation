@ui @profile
Feature: User profile
  A logged-in user views and updates their own profile details and password.

  # Source facts (docs/threatidr-source-facts.md section 9, views/profile/forms.py):
  #  - first/last name required Length(3,30); designation required Length(2,150); address <= 150;
  #    about <= 500; phone REQUIRED <= 15; email not editable; success "Profile updated successfully."
  #  - photo: jpg, jpeg, png, gif -> otherwise "Invalid photo"; request body limit 7 MB (413)
  #  - password change: current Length(6,40), wrong -> "Incorrect password"; new password Length(8,40)
  #    + complexity; confirm "Passwords must match"; success "Password updated successfully.";
  #    reusing the current password is allowed
  #
  # SAFETY: profile EDITS never run on the admin. The admin's designation is NULL (the header shows
  # "None", see dashboard.feature) and the profile form requires a designation, so an edit could
  # never be restored. Field edits run as the shared "Read Reports" fixture user (session cached,
  # no extra login, deleted at the end of the run) and are @serial-settings because they share that
  # user's profile. Password changes run as a disposable user that is created as admin, logged in
  # once (@serial-auth) and deleted afterwards, so a changed password never leaks into the fixture.
  #
  # Read live before implementing: WTForms 3 normally renders Length(min, max) as minlength /
  # maxlength attributes, so "rejected" in the length outline means "the value is not stored",
  # whether the browser truncates it or the server refuses it.

  @smoke @positive
  Scenario: Viewing my own profile shows my current details
    Given I am logged in as "admin"
    And I am on my profile page
    Then I should see my current profile details

  @regression @positive @serial-settings
  Scenario: Updating profile fields within their length limits persists
    Given I am logged in as a user with only the "Read Reports" permission
    And I am on my profile page
    When I update my profile with:
      | field       | value                      |
      | First Name  | QAA                        |
      | Designation | Security Analyst           |
      | Address     | 123 Test Street            |
      | About       | QA automation test account |
      | Phone       | +8801812345678             |
    Then my profile should show the updated values after reloading

  @regression @negative @serial-settings
  Scenario Outline: Profile field length limits are enforced
    Given I am logged in as a user with only the "Read Reports" permission
    And I am on my profile page
    When I update my profile "<field>" with a <length>-character value
    Then the profile update should be "<result>"

    Examples:
      | field       | length | result   |
      | First Name  | 2      | rejected |
      | First Name  | 3      | accepted |
      | First Name  | 30     | accepted |
      | First Name  | 31     | rejected |
      | Designation | 1      | rejected |
      | Designation | 151    | rejected |
      | Address     | 151    | rejected |
      | About       | 500    | accepted |
      | About       | 501    | rejected |
      | Phone       | 15     | accepted |
      | Phone       | 16     | rejected |

  @regression @negative
  Scenario: Phone is required on the profile form
    Given I am logged in as a user with only the "Read Reports" permission
    And I am on my profile page
    When I update my profile leaving "Phone" empty
    Then I should see a required-field error for "Phone"

  @regression @negative @serial-settings
  Scenario Outline: Profile photo type and size are enforced
    Given I am logged in as a user with only the "Read Reports" permission
    And I am on my profile page
    When I upload "<file>" as my profile photo
    Then the photo upload should be "<result>"

    Examples:
      | file         | result   |
      | notes.txt    | rejected |

    # Known defect, confirmed live 2026-10-19: a 7.5 MB image is not refused with 413; the server
    # goes on to save it (FileNotFoundError here only because the uploads directory is missing).
    @known-defect
    Examples: BUG - over the 7 MB request limit
      | file         | result   |
      | over-7mb.png | rejected |

    # Blocked in tidr-deploy-v1 (confirmed live 2026-10-19): /app/src/static/uploads/users/ does
    # not exist in the flask-prod container, so saving an accepted photo raises FileNotFoundError
    # and update_profile answers HTTP 500.
    @blocked-env
    Examples: accepted image types
      | file      | result   |
      | valid.png | accepted |
      | valid.gif | accepted |

  @regression @negative @security @serial-auth @serial-users
  Scenario: Changing my password with the wrong current password is rejected
    Given I am logged in as a disposable user
    And I am on my profile page
    When I change my password from "Wrong1!pass" to "Ab1!xyzz"
    Then I should see the password message "Incorrect password"

  @regression @negative @serial-auth @serial-users
  Scenario Outline: Password complexity boundary is enforced on password change
    Given I am logged in as a disposable user
    And I am on my profile page
    When I change my password to "<new password>"
    Then the password change should be "<result>"

    Examples:
      | new password | result   |
      | Ab1!xyz      | rejected |
      | Ab1!xyzz     | accepted |
      | abcdefg1!    | rejected |
      | ABCDEFG1!    | rejected |
      | Abcdefgh!    | rejected |
      | Abcdefg1     | rejected |

  @regression @positive @serial-auth @serial-users
  Scenario: Reusing the current password as the new password is accepted
    Given I am logged in as a disposable user
    And I am on my profile page
    When I change my password to my current password
    Then I should see the password message "Password updated successfully."
