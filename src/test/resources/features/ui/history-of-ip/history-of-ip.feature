@ui @history-of-ip
Feature: History of IP
  Shows DNS/threat activity history for a source IP or domain. No menu entry; reached by direct URL.

  # Source facts (views/history_of_ip/views.py:63-148): guard module "user". GET picks the source
  # IP with the most hits; POST ip_address must parse as an IP network or a domain, else flash
  # "IP address or Domain is invalid". The view renders history_of_ip/index.html but only
  # _history_of_ip/ ships, so every request returns HTTP 500 (confirmed live 2026-10-18).

  Background:
    Given I am logged in as "admin"

  @regression @positive @known-defect
  Scenario: BUG - visiting with no parameters shows the highest-activity source IP
    When I navigate directly to "/en/history_of_ip/"
    Then the page should load without a server error
    And history for the single highest-hit source IP should be shown

  @regression @positive @known-defect
  Scenario: BUG - looking up a specific valid IP shows its history
    When I look up "172.30.0.1" on the history of IP page
    Then the page should load without a server error
    And the history table should be scoped to "172.30.0.1"

  @regression @negative @known-defect
  Scenario: BUG - a malformed value is rejected before it reaches any query
    When I look up "not-an-ip-or-domain!!" on the history of IP page
    Then I should see the message "IP address or Domain is invalid"
