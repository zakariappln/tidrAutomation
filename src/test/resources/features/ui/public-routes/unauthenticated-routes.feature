@ui @public-routes @security
Feature: Unauthenticated routes
  A small set of routes are intentionally public (used by BIND and syslog integrations); every
  other route must redirect an anonymous visitor to the login page.

  # Source facts (views/home/views.py:183-203, 383-448; views/discover/views.py:71, 143):
  #  - /<lang>/nxdomain?host=<h> is the block page bind sends users to. It keeps the last two labels
  #    of host (clients6.google.com -> google.com), takes the NEWEST threat whose query_domain is
  #    LIKE %<that>%, and shows its THREATS display name, "Unknown" for an unmapped policy, or no
  #    threat type when nothing matches. {{host}} is autoescaped (templates/home/nxdomain.html)
  #  - /<lang>/syslog returns a plain-text THREAT line for the newest threat and, when syslog
  #    forwarding is configured, sends it (a refused connection sets is_forwarding=false in the DB:
  #    core/utils.py:719-732), so that scenario is @serial-settings. 500 when the threats table is
  #    empty
  #  - /threats/show-threat-year-month-chart and /threats/tooltip-threat-year-month-chart have no
  #    @login_required and answer WITHOUT a session (defect)
  #  - unknown URLs redirect to /<lang>/ rather than 404
  #  - /en/dashboard/, /en/users/, /en/settings/ and /en/dga_domains/ are covered by the active
  #    outline in auth/login.feature and are not repeated here

  @regression @positive
  Scenario: The nxdomain block page is public and escapes the host parameter
    When I navigate directly to "/en/nxdomain?host=<script>window.__qa=1</script>.example" without logging in
    Then I should not be on the login page
    And no script from the host parameter should have executed

  # The newest threat is read through POST /<lang>/threats/data (short_by=desc) or the CSV export,
  # never by opening /threats/ (that marks every threat viewed). The step stores it as the
  # scenario variables {newestThreatDomain} and {newestThreatType}.
  @regression @positive
  Scenario: The nxdomain block page names the threat type of the newest matching threat
    Given I have noted the destination domain and threat type of the newest threat
    When I navigate directly to "/en/nxdomain?host={newestThreatDomain}" without logging in
    Then the block page should show the threat type "{newestThreatType}"

  @regression @positive
  Scenario: The nxdomain block page shows no threat type for a host with no threat
    When I navigate directly to "/en/nxdomain?host={uniqueString}.invalid" without logging in
    Then the block page should show no threat type

  @regression @positive @serial-settings
  Scenario: The syslog route is public and returns a THREAT line without internal data
    When I call "/en/syslog" without an active session
    Then the anonymous response status should be 200
    And the anonymous response body should match "THREAT policy_name=.* source_id=.* domain=.* risk=.* risk_score=[0-4] app='Pipeline ThreatIDR'"
    And the anonymous response body should not contain "password"

  # All @login_required in source; confirmed live 2026-10-19: each answers 302 to /en/.
  @regression @negative @security
  Scenario Outline: Every other route redirects an anonymous visitor to login
    When I navigate directly to "<route>" without logging in
    Then I should be on the login page

    Examples:
      | route                    |
      | /en/reports/             |
      | /en/threats/             |
      | /en/blocked_domains/     |
      | /en/allowed_domains/     |
      | /en/user/profile         |
      | /en/history_of_ip/       |
      | /en/site_risk_indicator/ |

  # Known defect: neither route has @login_required (views/discover/views.py:71, 143). Observed
  # 2026-10-19 without a session: show-threat-year-month-chart answers 200 with data,
  # tooltip-threat-year-month-chart 415 (the view itself rejects the non-JSON GET) - neither redirects.
  @regression @negative @security @known-defect
  Scenario Outline: BUG - threat chart data is not served without a session
    When I call "<route>" without an active session
    Then the anonymous response should be a redirect to the login page

    Examples:
      | route                                       |
      | /en/threats/show-threat-year-month-chart    |
      | /en/threats/tooltip-threat-year-month-chart |
