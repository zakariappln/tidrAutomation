@ui @site-risk-indicator
Feature: Site Risk Indicator
  A lookup that fans a domain out to multiple external services (SSL info, WHOIS, tech stack,
  IP info, GitHub, dark web, Telegram, HaveIBeenPwned). No menu entry; reached by direct URL.

  # Source facts (views/site_risk_indicator/views.py:85-124): page guard is module "user"; search
  # needs "user-create". The page renders srisk_domain/index.html but only _srisk_domain/ ships, so
  # it returns HTTP 500 for permitted users (confirmed live 2026-10-18). Results come from
  # SITE_RISK_SERVER_URL; a failing source returns {"Error":"Something went wrong"} for that source.

  @regression @positive @known-defect
  Scenario: BUG - the Site Risk Indicator page loads for an administrator
    Given I am logged in as "admin"
    When I navigate directly to "/en/site_risk_indicator/"
    Then the page should load without a server error

  # Needs SITE_RISK_SERVER_URL pointing at a service where one source can be made to fail.
  @regression @negative @blocked-env @wip
  Scenario: A failing backing service does not break the whole lookup
    Given I am logged in as "admin"
    And one site-risk backing service is intentionally unreachable
    When I look up the domain "example.com" on the site risk indicator
    Then the unreachable source should show "Something went wrong"
    And the other sources should still return results

  @regression @negative @security
  Scenario: A user without any "user" permission is denied the Site Risk Indicator
    Given I am logged in as a user with only the "Read Reports" permission
    When I navigate directly to "/en/site_risk_indicator/"
    Then I should see an access-denied page
