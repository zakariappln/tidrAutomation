@ui @settings @email-templates
Feature: Email templates
  An administrator authors the rich-text email content used by alert notifications.

  # Source facts (docs/threatidr-source-facts.md section 10d, views/setting/forms.py:172-177):
  #  - template_name required (DB String(30)); content is CKEditor HTML with no validator and no
  #    server-side sanitisation; placeholders RECIPIENT, BLOCKCOUNT, BLOCKDETAILS
  #  - messages: "Email Template created successfully.", "Email template updated successfully.",
  #    "DELETED"; deleting a template referenced by an alert type probably fails on the foreign key
  #    (unconfirmed: depends on the migrations, see the fact sheet's unclear items)

  Background:
    Given I am logged in as "admin"
    And I am on the "Email Template" settings tab

  @smoke @positive
  Scenario: Creating an email template with rich-text content succeeds
    When I create an email template named "QA Tpl {uniqueString}" with bold and list content
    Then "QA Tpl {uniqueString}" should appear in the email templates list
    And opening "QA Tpl {uniqueString}" should show the same formatted content

  @regression @positive
  Scenario: Editing a template that is referenced by an alert type keeps the alert type linked
    Given an email template "QA Ref {uniqueString}" is referenced by an alert type
    When I rename the email template "QA Ref {uniqueString}" to "QA Ref2 {uniqueString}"
    Then I should see the template message "Email template updated successfully."
    And the alert type should still reference "QA Ref2 {uniqueString}"

  # Whether such a delete should be refused, cascade, or unlink the alert type is NOT decided
  # anywhere in source; only "no server error" is asserted until product decides.
  # Read live before implementing: what the UI shows for the FK failure.
  @regression @negative
  Scenario: Deleting a template that is referenced by an alert type does not crash
    Given an email template "QA InUse {uniqueString}" is referenced by an alert type
    When I delete the email template "QA InUse {uniqueString}"
    Then the request should not cause a server error

  # Observed 2026-10-19 (evidence attached by the steps): the content is STORED RAW
  # ("<img src=x onerror=...>"), but opening it in the editor (Edit; the View action is commented
  # out) is safe: CKEditor's content filter drops the handler ("<img data-cke-saved-src=x src=x>").
  # Not covered here: the commented-out View modal ($(...).html(content), settings.js:394) and the
  # alert e-mails built from the raw content.
  @regression @negative @security @critical
  Scenario: Script content in a template does not execute when rendered
    When I create an email template named "QA XSS {uniqueString}" whose content includes "<img src=x onerror=window.__qaXss=1>"
    And I open the email template "QA XSS {uniqueString}"
    Then no script from the template content should have executed
