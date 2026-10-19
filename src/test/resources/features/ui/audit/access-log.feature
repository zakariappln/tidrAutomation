@ui @audit @access-log
Feature: Access log
  A client-IP-grouped view of DNS query hit counts. No menu entry; reached by direct URL.

  # Source facts (core/app.py:40-44, 186-203): the access_log blueprint exists in the code but is
  # NOT registered, so /en/access_log/ falls through to the catch-all redirect (to the dashboard
  # when logged in). Nothing writes AccessLog rows anywhere. The two page scenarios are known
  # defects until the module is wired up; the view (views/access_log/views.py:25-80) groups
  # AccessLog rows by client_ip with a hit total and filters by date and ip_address.

  Background:
    Given I am logged in as "admin"

  @regression @positive @known-defect
  Scenario: BUG - the access log page loads and groups hits by client IP
    When I navigate directly to "/en/access_log/"
    Then the access log page should be shown, not a redirect to the dashboard
    And the table should group rows by client IP with hit counts

  @regression @positive @known-defect
  Scenario: BUG - filtering by date range and client IP narrows the results
    When I navigate directly to "/en/access_log/"
    And I filter the access log by the last 7 days and the client IP "172.30.0.1"
    Then every row shown should have client IP "172.30.0.1"

  # NOT a source-proven rule: views/access_log/views.py has only @login_required, no permission
  # check, and no permission key for this module exists (core/settings.py:217-236). Which
  # permission should gate the access log is an open product question; until it is answered this
  # scenario is not a defect report.
  @regression @negative @security @wip
  Scenario: The access log is reachable only by permitted users
    Given I am logged in as a user with only the "Read Reports" permission
    When I navigate directly to "/en/access_log/"
    Then I should see an access-denied page
