@ui @auth @license
Feature: License activation
  An administrator activates the ThreatIDR instance from the license page.

  # Source (templates/home/license.html, views/home/views.py:206-295): the license page has NO key
  # input. With no License row it shows "No Active License Found!" and an "Activate your license"
  # button that POSTs /<lang>/save-license with an empty body; the server then posts user_id and
  # product_id to <LICENSE_MANAGER_SERVICE_URL>/generate_license/tidr/ (5 s timeout). In this
  # deployment LICENSE_MANAGER_SERVICE_URL is blank, so the call raises, the server answers 404
  # with the plain text "Other error occurred: ...", and the page JS shows the toast
  # err.response.data.msg || "Failed to load license info" -> "Failed to load license info".
  # /<lang>/dashboard/license-info returns its default "No Licens Info found..." (typo in code)
  # while no License row exists (views/dashboard/apis/license_info.py:15).

  Background:
    Given I am logged in as "admin"

  @regression @positive
  Scenario: The license page offers activation while no license is stored
    Given I am on the license page
    Then I should see the text "No Active License Found!"
    And I should see the "Activate your license" button

  @regression @negative
  Scenario: Activation without a reachable license manager fails cleanly and stores nothing
    Given I am on the license page
    When I click "Activate your license"
    Then I should see the message "Failed to load license info"
    And the dashboard license info should be "No Licens Info found..."

  # Needs a reachable license manager service that issues a license for this subscription.
  @regression @positive @blocked-env @wip
  Scenario: Activation with a reachable license manager stores the license
    Given I am on the license page
    When I click "Activate your license"
    Then I should see the message "License activated successfully!"
    And the dashboard license info should not be "No Licens Info found..."
