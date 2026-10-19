@ui @dashboard
Feature: Dashboard overview
  The dashboard gives a security operator an at-a-glance view of DNS threat activity:
  detection counts, allow/block traffic, a 30-day trend, and top threats.

  # Source facts (docs/threatidr-source-facts.md section 2):
  #  - widget data comes from login-only JSON routes under /<lang>/dashboard/ (dashboard-statistics,
  #    total-threats-detected, allowed-requests, c2-threats-detected, ...)
  #  - the "Filter" menu (templates/dashboard/index.html:96-104) offers Last 24 hours / 48 hours /
  #    7 days / 15 days / 30 days / Custom range; choosing one reloads the widgets with
  #    {"range": "<n hours|days>"} and writes ?range=<value> into the URL
  #    (assets/js/scripts/dashboard.js:307-330)
  #  - empty Top Ten / Detected Categories panels show "No threat found yet! We are inspecting
  #    24/7." (templates/dashboard/components/topTenThreatDomains.html:46,
  #    detectedThreatCategories.html:30)
  #  - tile percentage (assets/js/scripts/dashboard_apis/getDashboardStatistics.js:46-66):
  #    average = 30_days_summery / 29, or 1 when 30_days_summery is 0; percentage =
  #    (24_hours_summery - average) / average * 100, shown as an absolute value with an up/down
  #    arrow, and 0% when either input is 0. "73001600%" (seen live 2026-10-18) is what that formula
  #    gives for ~730017 threats in 24 h with an empty 29-day baseline, so it is NOT proven to be a
  #    defect; whether a 1-threat baseline is acceptable is an open product question.
  #  - blocked_threat is copied from total_threat (apis/dashboard_statistics.py:31-34)
  #  - "Service Status" has no header widget any more (gone from the DOM since 2026-10-17)
  #  - page title "<page> | ThreatIDR - PIPELINE" (templates/layout.html:18); footer
  #    "<year> Copyright (c) PIPELINE All rights reserved." (templates/footer.html:55)

  Background:
    Given I am logged in as "admin"

  @smoke @positive
  Scenario: Dashboard loads all core widgets
    Then I should be on the dashboard
    And I should see the "Total Detection" tile
    And I should see the "C2 Threats Detected" tile
    And I should see the "Blocked Requests" tile
    And I should see the "Allowed Requests" tile
    And I should see the "30 Days DNS Activity Trend" chart
    And I should see the "Top Ten Threat Domains" panel
    And I should see the "Detected Threat Categories" panel

  @regression @positive
  Scenario: Choosing a range in the dashboard Filter menu reloads the widgets for that range
    When I choose "Last 7 days" from the dashboard Filter menu
    Then the dashboard widget requests should be sent with range "7 days"

  # Known defect seen live 2026-10-19: the widgets are reloaded with {"range": "7 days"}, but the URL
  # never gets ?range=: insertArgsToQueryString (assets/js/global.js:102-111) reassigns the loop's
  # const `key` and throws 'TypeError: "key" is read-only' on every Filter choice, so a reload or a
  # shared link loses the chosen range (assets/js/scripts/dashboard.js:307-330 expects ?range=).
  @regression @positive @known-defect
  Scenario: BUG - the dashboard filter keeps the range in the URL
    When I choose "Last 7 days" from the dashboard Filter menu
    Then the URL should contain the query parameter "range" with value "7 days"

  # The Custom range picker caps dates at today (maxDate, assets/js/scripts/dashboard.js:334-345), so
  # the step lifts that cap on the picker and fires the picker's own Apply handler.
  @regression @positive
  Scenario: A zero-threat period shows a friendly empty state, not a broken panel
    When I choose a custom dashboard range entirely in the future
    Then the "Top Ten Threat Domains" panel should show "No threat found yet! We are inspecting 24/7."
    And the "Detected Threat Categories" panel should show "No threat found yet! We are inspecting 24/7."

  @regression @positive
  Scenario Outline: Each tile's percentage change follows the dashboard-statistics data
    Then the "<tile>" percentage change should match the "<statistic>" values from dashboard-statistics

    Examples:
      | tile                | statistic      |
      | Total Detection     | total_threat   |
      | C2 Threats Detected | c2_threat      |
      | Blocked Requests    | blocked_threat |
      | Allowed Requests    | allowed_query  |

  # Read live before implementing: the dialog title opened by assets/js/scripts/modals/
  # statCardModals/allowedRequests.js.
  # Still @wip (glued, run 2026-10-19): nothing opens the "Allowed Requests Details" modal. Its only
  # trigger ("See All >", data-bs-toggle="modal") is commented out in the stat-card template and the
  # tile itself has no click handler, so clicking the tile does nothing. Product decision needed:
  # is the drill-down removed (drop the scenario) or broken (BUG scenario)?
  @regression @positive @wip
  Scenario: Drilling into a dashboard tile opens its detail table
    When I click the "Allowed Requests" tile
    Then the "Allowed Requests" detail dialog should open with a table

  # Known defect seen live 2026-10-18: the header renders the literal text "None" because the
  # bootstrap admin has no designation (designation is NULL). Nothing in the suite sets the admin's
  # designation (see profile.feature SAFETY note), so this stays meaningful.
  @regression @positive @known-defect
  Scenario: BUG - the header never shows a "None" placeholder for the designation
    Then the header user block should not contain the text "None"

  # Runs as the shared "Read Reports" fixture user, never the admin; @serial-settings because the
  # profile scenarios edit the same user's profile.
  @regression @positive @serial-settings
  Scenario: A configured user designation is shown in the header
    Given I am logged in as a user with only the "Read Reports" permission
    And my profile designation is set to "Security Analyst"
    When I reload the dashboard
    Then the header should show the designation "Security Analyst"

  @regression @positive
  Scenario: System status footer and latest-threat time are shown
    Then the sidebar should show the "System IP Address" with an IPv4 value
    And the sidebar should show the ThreatIDR version "v1.5.1"
    And the "Latest Threats Found" time should be shown

  @regression @positive
  Scenario: Application branding renders on every authenticated page
    Then the ThreatIDR logo should be visible in the sidebar
    And the browser title should end with "ThreatIDR - PIPELINE"
    And the footer should show "PIPELINE All rights reserved."
