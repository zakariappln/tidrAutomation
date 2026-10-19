@ui @threat-detection @serial-settings
Feature: Threat Detection
  A security operator reviews detected threats, filters/searches them, and opens an external
  threat-intel lookup (Discover panel) for a specific domain or IP.

  # Source facts (docs/threatidr-source-facts.md section 7, views/discover/views.py:416-560,
  # templates/discover/components/allThreatsTable.html):
  #  - table data: POST /<lang>/threats/data (DataTables) with threat, risk_category (Critical,
  #    High, Low, Gray, Info), date (dd/mm/yyyy-dd/mm/yyyy, default last 30 days), domain_count
  #    ("5 or less", 10..40, "40 above"; N = at most N hits per domain per day, 41 = more than 40)
  #  - the sort toggle (#sort-asc-desc -> short_by) orders by TIMESTAMP only, default newest first
  #  - the table search box (search[value]) only matches threat-type DISPLAY NAMES (THREATS in
  #    core/settings.py:139-164); a search text that matches no name applies NO filter. Searching by
  #    domain or IP is not implemented, so no scenario asserts it (open question for product)
  #  - SearchForm.ip_address (Length 3..25) is never rendered and never validated, and the source-IP
  #    filter is commented out, so there is no IP search field to test
  #  - the Risk Heatmap always loads the last 365 days from /threats/show-threat-year-month-chart
  #    (views/discover/views.py:71-90), independent of the table filter; the "No threats found!"
  #    dialog appears only when that 365-day series is empty (assets/js/scripts/discover.js:92-112)
  #  - opening /threats/ marks every threat viewed (views/discover/views.py:49): @serial-settings
  #  - live data exists (25k+ User Blocked rows on 2026-10-18), so filters can be asserted against it
  #  - Discover panel lookups need third-party API keys: OUT OF SCOPE (decision 2026-10-18)

  # Every scenario opens /threats/, which resets the notification bell; serialised with the bell test.

  Background:
    Given I am logged in as "admin"
    And I am on the threat detection page

  @smoke @positive
  Scenario: Threat detection page loads its core widgets
    Then I should see the "Risk Heatmap"
    And I should see the threats table

  # Read live before implementing: whether the table shows a per-domain hit count column; if not,
  # assert the domain-count filter through the request/response of POST /threats/data.
  @regression @positive
  Scenario: Combining Domain Count and date range filters narrows the table
    When I set the domain count filter to "20"
    And I set the date range to the last 7 days
    Then every row shown should be dated within the last 7 days
    And every row shown should have at most 20 hits for its domain on that day

  @regression @positive
  Scenario: The sort toggle switches the table between newest-first and oldest-first
    Then the threats table should be ordered by time newest first
    When I toggle the threats table sort order
    Then the threats table should be ordered by time oldest first

  # Still @wip, unglued (checked 2026-10-19): the page renders no search box. The search input is
  # commented out in templates/discover/components/allThreatsTable.html and the DataTables `dom`
  # option has no filter control, so a user cannot search; only window.table.search() from script
  # would send search[value]. Product decision needed: restore the box, or drop this scenario.
  @regression @positive @wip
  Scenario: Searching by a threat-type name shows only that threat type
    When I search the threats table for "User Blocked"
    Then every row shown should have threat type "User Blocked" or the table should be empty

  # The filter offers Critical, High, Low, Gray, Info (views/discover/forms.py:30-38); "Suspicious"
  # is not a risk level. A category with no rows must show the empty state instead.
  @regression @positive
  Scenario Outline: Filtering by risk category shows only that category
    When I filter the threats table by risk category "<category>"
    Then every row shown should have risk "<category>" or the table should be empty

    Examples:
      | category |
      | Critical |
      | High     |
      | Low      |
      | Gray     |
      | Info     |

  @regression @positive
  Scenario Outline: Filtering by threat type shows only that threat type
    When I filter the threats table by threat "<threat>"
    Then every row shown should have threat type "<threat>" or the table should be empty

    Examples:
      | threat       |
      | User Blocked |
      | User Allowed |
      | Malware      |
      | Botnet       |

  # The step expects the 12 month labels ending with the current month. The page computes the
  # heatmap start as getMonth()+2 with today's day (assets/js/scripts/discover.js getDateOneYearAgo),
  # which overflows in December (month "13") and around month-ends (e.g. Jan 29-31): a failure on
  # those dates is that app bug, not the step.
  @regression @positive
  Scenario: The Risk Heatmap always covers the last 365 days, whatever the table filter
    When I set the date range to the last 7 days
    Then the Risk Heatmap should cover the last 365 days

  @regression @positive
  Scenario: Paginating the threats table shows the correct subset of rows
    Given the threats table has more than one page of rows
    When I go to page 2 of the threats table
    Then the "Showing X to Y of Z entries" label should match the rows displayed

  # OUT OF SCOPE (decision 2026-10-18): Discover-panel lookups depend on third-party API keys.
  @regression @positive @security @out-of-scope @wip
  Scenario: The Discover panel shows external threat-intel results
    Given a threat exists for domain "known-threat.example"
    When I open the Discover panel for "known-threat.example"
    Then the VirusTotal, AbuseIPDB, IPinfo, Shodan, FraudGuard and AlienVaultOtx sections should load
    And the page should not contain any configured API key value

  @regression @negative @out-of-scope @wip
  Scenario: A failing third-party lookup degrades gracefully in the Discover panel
    Given the FraudGuard API key is not configured
    And a threat exists for domain "known-threat.example"
    When I open the Discover panel for "known-threat.example"
    Then the FraudGuard section should show an unavailable state
    And the other Discover sections should still load

  # Needs a threats table with no rows in the last 365 days; this environment has live data.
  @regression @negative @blocked-env @wip
  Scenario: The empty-threats dialog can be dismissed without blocking the page
    Given the threats table has no rows in the last 365 days
    When I am on the threat detection page
    Then I should see the message "No threats found!"
    When I dismiss the dialog
    Then the threats table filters should remain usable
