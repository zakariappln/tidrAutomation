@ui @audit @whitelist-blocklist-history
Feature: Whitelist/Blocklist history
  A read-only audit trail of every domain added to or removed from the Blocked and Allowed lists.

  # Source facts (views/whitelist_blocklist_history/views.py:24-35): guard "user-create"; the route
  # calls jsonify() on a SQLAlchemy query and returns HTTP 500 TypeError (confirmed live 2026-10-18).
  # The same history is shown correctly in Settings > User Log, which is where the audit content is
  # verified (settings/logs-and-support.feature and the domain delete scenarios). The User Log
  # renders Event Type as "User <event_type|capitalize>" and Action Type capitalised ("Create" /
  # "Delete") (templates/setting/partials/alert_settings/logs.html).

  Background:
    Given I am logged in as "admin"

  @regression @positive
  Scenario: Every add and delete on Blocked and Allowed Domains is captured in the User Log
    Given I add and then delete the blocked domain "qa-hist-b-{uniqueString}.example"
    And I add and then delete the allowed domain "qa-hist-a-{uniqueString}.example"
    When I am on the "User Log" settings tab
    Then the user log should contain:
      | domain                           | event type   | action |
      | qa-hist-b-{uniqueString}.example | User Blocked | Create |
      | qa-hist-b-{uniqueString}.example | User Blocked | Delete |
      | qa-hist-a-{uniqueString}.example | User Allowed | Create |
      | qa-hist-a-{uniqueString}.example | User Allowed | Delete |

  @regression @positive @known-defect
  Scenario: BUG - the whitelist/blocklist history route returns the history, not a server error
    When I navigate directly to "/en/whitelist_blocklist_history/"
    Then the page should load without a server error
