@ui @settings @logs-support
Feature: User log, system log, help & support, and diagnostics
  An administrator reviews the audit trail, reaches the syslog configuration, finds support
  information, and downloads a diagnostics bundle for troubleshooting.

  # Source facts (docs/threatidr-source-facts.md section 10e,
  # templates/setting/partials/alert_settings.html, .../logs.html):
  #  - User Log is built from WhitelistBlocklistHistory (domain add/delete only, NOT user creation),
  #    newest first, 20 rows per DataTable page. Columns: Domain, Event Type ("User Blocked" /
  #    "User Allowed"), Action Type ("Create" / "Delete", capitalised), Created By (a deleted actor
  #    shows as "Deleted User"), Date
  #  - the "System Log" tab (href="#syslog") is NOT a log viewer: it holds the "Syslog
  #    Configuration" form (Server IP, Server Port, forwarding and TCP checkboxes), covered in
  #    syslog-and-dns-bypass.feature
  #  - Send Diagnostics: GET|POST /<lang>/settings/send-diagnostics is login-only and returns
  #    /tmp/threatidr-diagnostics-<date>.bin (tar.gz) with the grok log, grok config (database and
  #    redis sections stripped), nginx error log, /var/log/messages, bind.log, rpz.log, redis log and
  #    machine-status.txt. nginx/redis/bind run in other containers, so parts may be missing.

  Background:
    Given I am logged in as "admin"

  # Other scenarios write to the same log in parallel, so the entry is looked up by its unique
  # domain, not assumed to be the newest row.
  @regression @positive
  Scenario: User Log reflects a just-performed domain action
    Given "qa-log-{uniqueString}.example" is already a blocked domain
    When I am on the "User Log" settings tab
    Then the user log should contain:
      | domain                        | event type   | action |
      | qa-log-{uniqueString}.example | User Blocked | Create |
    And the user log entry for "qa-log-{uniqueString}.example" should name the admin as "Created By"

  @regression @positive
  Scenario: The System Log tab shows the syslog configuration form
    When I am on the "System Log" settings tab
    Then I should see the text "Syslog Configuration"
    And I should see the "Server IP" and "Server Port" fields

  # Observed 2026-10-19: the pane has no links at all (only "Get Diagnostics"), so this passes
  # vacuously; the step attaches the link count to the report.
  @regression @positive
  Scenario: Help & Support tab renders without broken links
    When I am on the "Help & Support" settings tab
    Then every link on the help tab should answer with a non-error status

  # Read live before implementing: which of the other components are present in this deployment
  # (nginx, redis and bind run in other containers). The step attaches the archive listing to the
  # Allure report; only the parts the app itself always writes are asserted.
  @regression @negative
  Scenario: The diagnostics archive is readable and carries no credentials
    When I trigger Send Diagnostics
    Then the downloaded archive should be a readable tar.gz
    And the archive should contain "machine-status.txt"
    And the archive should not contain database or redis credentials

  # Known defect: send-diagnostics is login-only (views/setting/views.py:1104); a user holding only
  # "Read Reports" can download system logs including /var/log/messages.
  @regression @negative @security @known-defect
  Scenario: BUG - Send Diagnostics is denied to a user without settings permission
    Given I am logged in as a user with only the "Read Reports" permission
    When I request Send Diagnostics directly
    Then the request should be denied
