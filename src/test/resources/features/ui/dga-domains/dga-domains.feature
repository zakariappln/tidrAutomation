@ui @dga-domains
Feature: DGA domains
  ThreatIDR predicts Domain Generation Algorithm (DGA) domains from recent DNS traffic. This
  screen has no menu entry; it is reached only by direct URL.

  # Source facts (views/dga_domain/views.py:40-88): guard is module "dga", which only the
  # Administrator ("*") role can hold. The view renders dga_domain/index.html, but the build ships
  # only templates/_dga_domain/, so the page returns HTTP 500 TemplateNotFound for administrators
  # (confirmed live 2026-10-18). The permission check still renders 401.html for other roles.

  @regression @positive @known-defect
  Scenario: BUG - an administrator can view predicted DGA domains
    Given I am logged in as "admin"
    When I navigate directly to "/en/dga_domains/"
    Then the page should load without a server error
    And I should see the DGA-predicted domains list

  @regression @negative @security
  Scenario: A user without the "dga" permission is denied
    Given I am logged in as a user holding every grantable permission
    When I navigate directly to "/en/dga_domains/"
    Then I should see an access-denied page
