@ui @domains @allowed-domains
Feature: Allowed Domains
  An administrator maintains a list of domains that should always resolve normally, exempted
  from RPZ enforcement.

  # Source facts (docs/threatidr-source-facts.md sections 4-5, views/allowed_domain/):
  #  - domain: required, Length(max=255), stripped, then validators.domain(); duplicate check is
  #    case-insensitive: "Whitelisted domain already added"
  #  - a domain on the Blocklist is refused: "This domain is already in the Blocklist. Remove it
  #    from the Blocklist first." (so one domain can never be on both lists)
  #  - new rows are Inactive; Apply Changes ("Generate") sets them Active and writes zone
  #    whitelisted.host.ppln with "<d> CNAME rpz-passthru." and "*.<d> CNAME rpz-passthru."
  #  - list: 10 rows per page, newest first
  #  - Apply Changes activates EVERY Inactive row and rewrites the zone for everyone, so the scenario
  #    that clicks it is @serial-settings
  #  - RPZ zones are ordinary zones on bind9, so the passthru rule is verified by querying
  #    "<d>.whitelisted.host.ppln" directly with DnsClient (no docker exec needed)

  Background:
    Given I am logged in as "admin"
    And I am on the allowed domains page

  # @serial-settings: an Apply Changes running in parallel would flip this row to Active.
  @smoke @positive @serial-settings
  Scenario: Adding a valid domain lists it as a new entry
    When I add the allowed domain "qa-allow-{uniqueString}.example"
    Then "qa-allow-{uniqueString}.example" should appear in the allowed domains list
    And its status should be "Inactive"

  @regression @negative
  Scenario: Adding a domain that is already allowed is rejected, regardless of case
    Given "qa-allow-dup-{uniqueString}.example" is already an allowed domain
    When I add the allowed domain "QA-ALLOW-DUP-{uniqueString}.EXAMPLE"
    Then I should see the domain error "Whitelisted domain already added"

  @regression @negative
  Scenario Outline: Domain format is validated before it can be added
    When I add the allowed domain "<domain>"
    Then I should see the domain error "Please enter a valid domain"

    Examples:
      | domain         |
      | not a domain   |
      | invalid_domain |
      | domain..com    |
      | -bad.example   |

  # Length(max=255) (views/allowed_domain/forms.py:35). WTForms 3 renders it as maxlength="255" on
  # the input, so the browser cannot hold 256 characters; the server-side message for a longer
  # value is the WTForms default "Field cannot be longer than 255 characters.". A 255-character
  # name is still not a valid domain (validators.domain caps a name at 253), so it gets the format
  # error, never the length error. Read live before implementing: confirm the maxlength attribute.
  @regression @negative
  Scenario: The domain field never accepts more than 255 characters
    When I type a 256-character value into the allowed domain field
    Then the allowed domain field should hold at most 255 characters
    When I add an allowed domain that is 255 characters long
    Then I should not see the domain error "Field cannot be longer than 255 characters."

  @regression @positive
  Scenario: Deleting an allowed domain removes it and is recorded in the history log
    Given "qa-allow-del-{uniqueString}.example" is already an allowed domain
    When I delete the allowed domain "qa-allow-del-{uniqueString}.example"
    Then "qa-allow-del-{uniqueString}.example" should no longer appear in the allowed domains list
    And the user log should contain:
      | domain                              | event type   | action |
      | qa-allow-del-{uniqueString}.example | User Allowed | Delete |

  @regression @positive @security @critical @serial-settings
  Scenario: Applying changes writes a working RPZ passthru rule for the domain
    Given I add the allowed domain "qa-allow-dns-{uniqueString}.example"
    When I click "Apply Changes"
    Then its status should be "Active"
    And the RPZ zone "whitelisted.host.ppln" should map "qa-allow-dns-{uniqueString}.example" to "rpz-passthru."
    And the RPZ zone "whitelisted.host.ppln" should map "*.qa-allow-dns-{uniqueString}.example" to "rpz-passthru."

  @regression @negative
  Scenario: A domain that is already blocked cannot be added to the allowed list
    Given "qa-both-{uniqueString}.example" is already a blocked domain
    When I add the allowed domain "qa-both-{uniqueString}.example"
    Then I should see the domain error "This domain is already in the Blocklist. Remove it from the Blocklist first."

  # 10 per page ordered by create_at descending; other scenarios add rows in parallel, so order is
  # asserted on the "Creation Date" column, never on which domain comes first.
  @regression @positive
  Scenario: Paginating the allowed domains list shows ten rows per page, newest first
    Given more than 10 allowed domains exist
    When I reload the allowed domains page
    Then the allowed domains list should show 10 rows
    And the allowed domains list should be ordered by "Creation Date" newest first
    When I go to page 2 of the allowed domains list
    Then the allowed domains pager should show page 2 as current
