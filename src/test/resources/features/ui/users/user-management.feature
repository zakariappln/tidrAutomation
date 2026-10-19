@ui @users @serial-users
Feature: User management
  An administrator creates, edits, searches and deletes ThreatIDR user accounts.

  # Confirmed live 2026-10-17:
  #  - First/Last Name are rejected client-side unless letters only ("Last Name is not valid,
  #    input text only.") - digits, hyphens and apostrophes all fail, so real surnames like
  #    "Smith-Jones" or "O'Brien" cannot be entered. Worth raising as a product issue; test data
  #    here therefore keeps names letters-only and relies on {uniqueEmail} for uniqueness
  #    ({uniqueString} is "qa-<hex>" and must NOT be used in a name field).
  #  - Email goes through WTForms Email() (python email_validator), which rejects reserved
  #    special-use TLDs such as .test - {uniqueEmail} uses example.com for that reason.
  #
  # Source facts (docs/threatidr-source-facts.md section 3, views/user/forms.py):
  #  - create: email required + Email + Length(6,40), unique (exact case) "Email already registered";
  #    first/last name required Length(2,30); password Length(8,40) + complexity regexp; confirm
  #    "Passwords must match"; designation (Title) OPTIONAL Length(max=150); phone Length(max=15)
  #  - password complexity message (verbatim): "Your password must be at least 8 characters, Must
  #    have at least 1 number, Must have at least 1 upper case character, Must have at least 1 lower
  #    case character, Must have at least 1 special character."
  #  - edit: first/last Length(3,30), designation REQUIRED, phone Length(max=20) but the DB column
  #    is String(15) -> 16..20 characters crash (defect)
  #  - photo: jpg, jpeg, png, gif only -> "Invalid photo!"; request body limit 7 MB (413)
  #  - list: 9 per page, search is case-insensitive ilike on first/last name only, never email
  #    (views/user/views.py:39-51)
  #  - "a non-admin user exists" must create the user WITH a designation and names of 3+ letters,
  #    otherwise the edit form (designation required, names min 3) cannot be submitted at all.
  #
  # Read live before implementing: WTForms 3 normally renders Length(min, max) as minlength /
  # maxlength attributes, so an over-long value may be truncated by the browser instead of being
  # refused by the server. In the boundary outlines "rejected" therefore means "no user is stored
  # with that exact value", whichever layer stops it.
  #
  # ASSUMPTIONS (confirm or correct):
  #  - the seeded Super Administrator always has user id 1; some scenarios below depend on that
  #  - whether role-downgrade protection for user id 1 can be bypassed by submitting the edit form
  #    with the role field blank is an OPEN QUESTION from source review, not yet reproduced live -
  #    treat a bypass as a confirmed Critical defect if the scenario below ever goes green

  Background:
    Given I am logged in as "admin"
    And I am on the users page

  @smoke @positive
  Scenario: Creating a user with all mandatory fields succeeds
    When I create a user with:
      | field            | value                        |
      | First Name       | QA                           |
      | Last Name        | Tester                       |
      | Email            | {uniqueEmail}                |
      | Password         | Ab1!xyzz                     |
      | Confirm Password | Ab1!xyzz                     |
      | Title            | QA Engineer                  |
    Then the user "{uniqueEmail}" should appear in the users list

  @regression @negative
  Scenario Outline: Creating a user with a missing mandatory field is rejected
    When I create a user leaving "<field>" empty
    Then I should see a required-field error for "<field>"

    Examples:
      | field            |
      | First Name       |
      | Last Name        |
      | Email            |
      | Password         |
      | Confirm Password |

  @regression @positive
  Scenario: Title (designation) is optional when creating a user
    When I create a user leaving "Title" empty
    Then the user creation should be "accepted"

  @regression @negative
  Scenario Outline: Name and email length boundaries are enforced on user creation
    When I create a user with "<field>" set to a <length>-character value
    Then the user creation should be "<result>"

    Examples:
      | field      | length | result   |
      | First Name | 1      | rejected |
      | First Name | 2      | accepted |
      | First Name | 30     | accepted |
      | First Name | 31     | rejected |
      | Last Name  | 1      | rejected |
      | Last Name  | 31     | rejected |
      | Email      | 40     | accepted |
      | Email      | 41     | rejected |

  # Read live before implementing: the email input may be type="email", in which case the browser
  # blocks "not-an-email" before the server's Email() validator ("Invalid email address.") runs.
  @regression @negative
  Scenario: Creating a user with a malformed email is rejected
    When I create a user with email "not-an-email"
    Then the user creation should be "rejected"

  @regression @negative
  Scenario: Creating a user with an already-registered email is rejected
    When I create a user with email "admin@threatidr.com"
    Then I should see the user form error "Email already registered"

  # Confirmed live 2026-10-19: users.js registerUser() compares the two fields before sending, so
  # the server's "Passwords must match" is never reached from the UI; the page shows its own text.
  @regression @negative
  Scenario: Mismatched password and confirmation is rejected
    When I create a user with password "Ab1!xyzz" and confirm password "Different1!"
    Then I should see the user form error "Password didn't match. Please re-enter the password correctly."

  @regression @negative
  Scenario Outline: Password complexity is enforced on user creation
    When I create a user with password "<password>"
    Then I should see a password complexity error

    Examples:
      | password  | missing rule       |
      | abcdefg1! | uppercase letter   |
      | ABCDEFG1! | lowercase letter   |
      | Abcdefgh! | digit              |
      | Abcdefg1  | special character  |
      | Ab1!xyz   | minimum length (8) |

  # Confirmed live 2026-10-19: the phone input is an intl-tel-input widget with a separate dial code
  # (+880 for /en/), and users.js submits "+<dial code> <typed>". The server's 15-character limit
  # therefore applies to "+880 " plus the typed digits: 10 digits is the most that can be saved,
  # while the input itself (maxlength 15) lets the user type 15.
  @regression @negative
  Scenario Outline: Phone number length is enforced on user creation
    When I create a user with phone "<phone>"
    Then the user creation should be "<result>"

    Examples:
      | phone            | result   |
      | 1812345678       | accepted |
      | 1234567890123456 | rejected |

  # Known defect (see above): 15 typed digits become "+880 " + 15 = 20 characters and the server
  # answers "Field cannot be longer than 15 characters."
  @regression @negative @known-defect
  Scenario: BUG - a 15-digit phone number that the phone field accepts can be saved
    When I create a user with phone "123456789012345"
    Then the user creation should be "accepted"

  # Known defect: the edit form allows 20 characters but the column is String(15)
  # (views/user/forms.py:75-122, models/user.py:36), so 16..20 characters raise a database error
  # instead of a validation message.
  @regression @negative @known-defect
  Scenario: BUG - a 16-character phone on edit is rejected with a validation message
    Given a non-admin user exists
    When I edit that user's phone number to "1234567890123456"
    Then I should see a validation error for "Phone"
    And the request should not cause a server error

  @regression @negative
  Scenario Outline: Profile photo type and size are enforced on user creation
    When I create a user with the photo "<file>"
    Then the user creation should be "<result>"

    Examples:
      | file                  | result   |
      | script.exe-as.png.exe | rejected |
      | notes.txt             | rejected |

    # Known defect, confirmed live 2026-10-19: a 7.5 MB image is not refused with 413; the server
    # goes on to save it (FileNotFoundError here only because the uploads directory is missing).
    @known-defect
    Examples: BUG - over the 7 MB request limit
      | file         | result   |
      | over-7mb.png | rejected |

    # Blocked in tidr-deploy-v1 (confirmed live 2026-10-19): /app/src/static/uploads/users/ does
    # not exist in the flask-prod container, so saving any accepted photo raises FileNotFoundError
    # and the register request answers HTTP 500.
    @blocked-env
    Examples: accepted image types
      | file      | result   |
      | valid.png | accepted |
      | valid.jpg | accepted |

  @regression @positive
  Scenario: Searching users matches first or last name case-insensitively, never email
    Given a user exists with a unique letters-only last name
    When I search users for that last name in lower case
    Then that user should be the only user listed
    When I search users for that user's email
    Then no users should be listed

  @regression @positive
  Scenario: Editing a user persists the changes
    Given a non-admin user exists
    When I edit that user's phone number to "+8801812345678"
    Then reloading the user should show the phone number "+8801812345678"

  # Costs one real login for the second session, hence @serial-auth.
  @regression @negative @security @serial-auth
  Scenario: Deleting a user immediately revokes their active session
    Given a non-admin user exists and is logged in in a separate session
    When I delete that user
    Then their active session should be redirected to the login page on its next action

  # @destructive: these exercise the guards protecting the only admin account. The user approved
  # running them against this local environment, and they are written so a PASS leaves the admin
  # intact (the app rejects the action) while a broken guard FAILS loudly.
  #
  # Confirmed live 2026-10-17: the seeded admin (id 1) does NOT appear in its own Users list at all,
  # so there is no delete/edit control to click - the only real protection is server-side. These
  # scenarios therefore assert the backend directly, over the logged-in session (same cookies +
  # the page's X-CSRFToken meta). Delete returns 403 "Sorry! Can not delete the Admin user"; a role
  # downgrade (role != Administrator) is rejected by form validation with "Sorry! Can not change
  # this role", and a follow-up read confirms the role is unchanged.
  #
  # SAFETY for every PUT /update_user/1 below: the update view overwrites first/last name,
  # designation, phone, address, about and is_active from the submitted form
  # (views/user/views.py:362-372). The request must resend the admin's CURRENT values with
  # is_active_edit "Yes". The non-admin target role must hold "Update Users", so that if the guard
  # is broken the still-logged-in admin can PUT role 1 back in cleanup (role 1 passes the guard).
  @regression @negative @security @destructive
  Scenario: The seeded Super Administrator cannot be deleted
    When I send a request to delete the Super Administrator account
    Then the delete request should be rejected with "Sorry! Can not delete the Admin user"
    And the Super Administrator account should still exist

  @regression @negative @security @critical @destructive
  Scenario: The seeded Super Administrator's role cannot be downgraded
    When I send a request to change the Super Administrator's role to a non-admin role
    Then the role-change request should be rejected with "Sorry! Can not change this role"
    And the Super Administrator's role should still be Administrator

  @regression @negative @security @critical @destructive
  Scenario: The Super Administrator's role cannot be changed by omitting the role field
    When I send a request to update the Super Administrator with the role field blank
    Then the Super Administrator's role should still be Administrator

  # Known defect: the guard looks the user up by the SUBMITTED email, not the URL id
  # (views/user/forms.py:116-120; the view-side check is a no-op, views/user/views.py:374-375), so
  # sending another user's email to /update_user/1 skips it.
  @regression @negative @security @critical @wip @destructive @known-defect
  Scenario: BUG - the Super Administrator's role cannot be changed by submitting another user's email
    Given a non-admin user exists
    When I send a request to change user 1's role using that user's email in the form
    Then the Super Administrator's role should still be Administrator

  @regression @positive
  Scenario: The users list can be switched between list view and card view
    When I switch the users list to card view
    Then the users should be shown as cards
    When I switch the users list to list view
    Then the users should be shown as table rows
