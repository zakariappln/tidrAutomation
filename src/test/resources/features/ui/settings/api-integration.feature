@ui @settings @api-integration @out-of-scope
Feature: Third-party API integration settings
  An administrator stores the API keys used by Threat Detection's Discover panel and the Site
  Risk Indicator (VirusTotal, AbuseIPDB, IPinfo, Shodan, FraudGuard, AlienVaultOtx, Censys).

  # OUT OF SCOPE (decision 2026-10-18): API-key related cases are not automated. Kept as the
  # specification. Source facts (views/setting/views.py:981-1007, forms.py:209-211): every key is
  # optional and stored in plain text; the FraudGuard password is re-rendered into the page
  # (PasswordInput(hide_value=False)).

  Background:
    Given I am logged in as "admin"
    And I am on the "API Integration" settings tab

  @smoke @positive @wip
  Scenario: Saving valid API keys for each provider persists
    When I save API keys for VirusTotal, AbuseIPDB, IPinfo, Shodan and AlienVaultOtx
    Then reloading the settings page should show each key saved

  @regression @negative @security @manual @wip
  Scenario: The FraudGuard credential fields do not get populated by unrelated browser autofill
    Given the browser has the ThreatIDR admin login saved for autofill
    When I open the API integration tab with autofill enabled
    Then the FraudGuard username and password fields should be empty

  @regression @negative @wip
  Scenario: A blank provider key degrades gracefully in a dependent lookup
    Given the FraudGuard API key is left blank
    When I trigger a lookup that depends on FraudGuard
    Then the FraudGuard section should show a "not configured" state, not a crash
