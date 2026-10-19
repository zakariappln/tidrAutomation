@ui @settings @alerts
Feature: Alert settings and alert types
  An administrator configures who gets notified (Alert Settings) and defines the alert types that
  can trigger a notification (Alert List), each linked to an email template.

  # Source facts (docs/threatidr-source-facts.md section 10b, views/setting/forms.py:54-170):
  #  - notification email: Tagify JSON or CSV; invalid -> "Invalid email address(es): a, b".
  #    The view then json.loads() the value (views/setting/views.py:541), so a CSV string passes
  #    validation and crashes (defect).
  #  - alert type (AlertCategoryForm): "Alert Name" required (no form limit, DB String(30)
  #    models/setting.py:40 -> 31+ chars crash), "Alert Period" No Alert (52560000) / Every 5
  #    minutes / Every 15 minutes / Every 30 minutes / Every Hour / Every 2 Hour / Every Day,
  #    "Email Template" coerce=int with choices from the DB (no DataRequired), categories optional.
  #    Messages: "AlertType created successfully.", "Alert Type updated successfully", "Deleted".
  #  - the bell: Celery pushes {html[], total, id[]} for unviewed threats every minute on
  #    /notifysocket; opening the feed POSTs /dashboard/notification-update {"notify_id":[ids]},
  #    which sets is_viewed=true for exactly those ids; opening /threats/ marks EVERY threat viewed.

  Background:
    Given I am logged in as "admin"

  # Blocked in this environment (observed 2026-10-19): a save also rewrites the stored "System
  # Notification Sound" flag, which is only readable from the bell's socket pushes, and no push
  # arrives here (the Celery worker has consumed no task since 2026-10-18), so the original flag
  # could not be restored exactly. Glued; runs once pushes work (the cleanup reads the flag then).
  @smoke @positive @serial-settings @blocked-env @wip
  Scenario: Saving a single valid notification email persists
    Given the original alert settings are recorded for restore
    And I am on the "Alert Settings" settings tab
    When I set the notification email to "qa-alerts@example.com"
    And I click "Save"
    Then reloading the settings page should show "qa-alerts@example.com" as the notification email

  @regression @negative @serial-settings
  Scenario: Saving a mix of valid and malformed notification emails is rejected
    Given the original alert settings are recorded for restore
    And I am on the "Alert Settings" settings tab
    When I set the notification emails to "qa-alerts@example.com" and "not-an-email"
    And I click "Save"
    Then I should see the alert settings error "Invalid email address(es): not-an-email"
    And "not-an-email" should not be stored as a notification recipient

  # Known defect: CSV passes validate_multiple_emails, then json.loads() raises (views/setting/
  # views.py:541). Sent as a request over the admin session because Tagify always submits JSON.
  @regression @negative @known-defect @serial-settings
  Scenario: BUG - comma-separated notification emails are saved without a server error
    Given the original alert settings are recorded for restore
    And I am on the "Alert Settings" settings tab
    When I submit the alert settings with the email value "a@example.com,b@example.com"
    Then the request should not cause a server error

  @regression @positive
  Scenario: Creating an alert type with the "No Alert" period succeeds
    Given I am on the "Alert List" settings tab
    When I create an alert type named "QA NoAlert {uniqueString}" with period "No Alert"
    Then "QA NoAlert {uniqueString}" should appear in the alert types list with period "No Alert"

  @regression @negative
  Scenario: Creating an alert type without a name is rejected
    Given I am on the "Alert List" settings tab
    When I create an alert type leaving the name empty
    Then I should see a required-field error for "Alert Name"

  # Read live before implementing: the Email Template select may preselect its first option, in
  # which case "without choosing" means clearing it; the server answer is the WTForms SelectField
  # error, not a DataRequired message, so only the outcome is asserted.
  @regression @negative
  Scenario: Creating an alert type without an email template is rejected
    Given I am on the "Alert List" settings tab
    When I create an alert type named "QA NoTpl {uniqueString}" without choosing an email template
    Then "QA NoTpl {uniqueString}" should not appear in the alert types list

  # Known defect: no form length limit, DB column String(30) (models/setting.py:40). The name is
  # built from {uniqueString} padded to exactly 31 characters by the step.
  @regression @negative @known-defect
  Scenario: BUG - an alert type name longer than 30 characters is rejected with a message
    Given I am on the "Alert List" settings tab
    When I create an alert type whose name is 31 characters long
    Then I should see a validation error for "Alert Name"
    And the request should not cause a server error

  # The list renders periods in minutes (alert_types.html:61: "Every Hour" shows as 60) and only
  # 52560000 as "No Alert"; the step maps the period label to that cell text via the form's option.
  @regression @positive
  Scenario: Editing and deleting an alert type works
    Given an alert type named "QA Edit {uniqueString}" exists
    And I am on the "Alert List" settings tab
    When I change the period of the alert type "QA Edit {uniqueString}" to "Every Hour"
    Then I should see the alert list message "Alert Type updated successfully"
    And "QA Edit {uniqueString}" should appear in the alert types list with period "Every Hour"
    When I delete the alert type "QA Edit {uniqueString}"
    Then "QA Edit {uniqueString}" should not appear in the alert types list

  # New threats keep arriving, so the bell count is not expected to reach zero; the assertion is
  # that the ids just opened are no longer reported as unviewed.
  # Blocked in this environment (observed 2026-10-19): no "notify" push reaches the browser (only
  # socket.io pings in 75 s across a minute boundary), so the bell never shows unread alerts.
  @regression @positive @wip @serial-settings @blocked-env
  Scenario: Opening the alert notification feed marks the shown alerts as viewed
    Given at least one unread alert is shown in the bell
    When I open the alert notification feed
    Then the page should send a notification update for the alerts shown in the feed
    And the next notification push should not list those alerts as unviewed
