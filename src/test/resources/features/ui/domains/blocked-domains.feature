@ui @domains @blocked-domains
Feature: Blocked Domains
  An administrator maintains a list of domains that ThreatIDR should block at the DNS level.

  # Regression context:
  #  - On 2026-09-27, Apply Changes had NO effect on real DNS resolution: a domain marked Active
  #    never reached BIND's RPZ zone file. Fixed by 2026-10-17 (verified live: adding+applying a
  #    domain produces an NXDOMAIN, confirmed against the bind9 container directly).
  #  - The DNS propagation scenario is the single highest-value regression check in this feature.
  #    It asserts a real DNS response via core/dns/DnsClient (dnsjava) querying dns.host:dns.port.
  #
  # Source facts (docs/threatidr-source-facts.md sections 4-5, views/blocked_domain/):
  #  - domain: required, Length(max=255), stripped, then validators.domain() -> "Please enter a
  #    valid domain"; duplicates are case-insensitive -> "Blocked domain already added"
  #  - a domain on the Allowlist is refused: "This domain is already in the Allowlist. Remove it
  #    from the Allowlist first."
  #  - Apply writes "<d> CNAME ." and "*.<d> CNAME ." into pplnbl.host.ppln, so subdomains block too
  #  - delete does NOT rewrite the zone; the domain keeps resolving NXDOMAIN until the next Apply
  #  - list: 10 rows per page, newest first
  #  - Apply Changes activates EVERY Inactive row and rewrites the zone for everyone, so scenarios
  #    that click it are @serial-settings; an Apply running in parallel can flip another scenario's
  #    freshly added row to "Active" before it asserts "Inactive"

  Background:
    Given I am logged in as "admin"
    And I am on the blocked domains page

  # @serial-settings: an Apply Changes running in parallel would flip this row to Active.
  @smoke @positive @serial-settings
  Scenario: Adding a valid domain lists it as a new entry
    When I add the blocked domain "qa-block-{uniqueString}.example"
    Then "qa-block-{uniqueString}.example" should appear in the blocked domains list
    And its status should be "Inactive"

  @regression @negative
  Scenario: Adding a domain that is already blocked is rejected, regardless of case
    Given "qa-block-dup-{uniqueString}.example" is already a blocked domain
    When I add the blocked domain "QA-BLOCK-DUP-{uniqueString}.EXAMPLE"
    Then I should see the domain error "Blocked domain already added"

  @regression @negative
  Scenario Outline: Domain format is validated before it can be added
    When I add the blocked domain "<domain>"
    Then I should see the domain error "Please enter a valid domain"

    Examples:
      | domain         |
      | not a domain   |
      | invalid_domain |
      | domain..com    |
      | -bad.example   |

  # Length(max=255) (views/blocked_domain/forms.py:35). WTForms 3 renders it as maxlength="255" on
  # the input, so the browser cannot hold 256 characters; the server-side message for a longer
  # value is the WTForms default "Field cannot be longer than 255 characters.". A 255-character
  # name is still not a valid domain (validators.domain caps a name at 253), so it gets the format
  # error, never the length error. Read live before implementing: confirm the maxlength attribute.
  @regression @negative
  Scenario: The domain field never accepts more than 255 characters
    When I type a 256-character value into the blocked domain field
    Then the blocked domain field should hold at most 255 characters
    When I add a blocked domain that is 255 characters long
    Then I should not see the domain error "Field cannot be longer than 255 characters."

  @regression @negative
  Scenario: A domain that is already allowed cannot be added to the blocked list
    Given "qa-both-b-{uniqueString}.example" is already an allowed domain
    When I add the blocked domain "qa-both-b-{uniqueString}.example"
    Then I should see the domain error "This domain is already in the Allowlist. Remove it from the Allowlist first."

  @regression @positive
  Scenario: Deleting a blocked domain removes it and is recorded in the history log
    Given "qa-block-del-{uniqueString}.example" is already a blocked domain
    When I delete the blocked domain "qa-block-del-{uniqueString}.example"
    Then "qa-block-del-{uniqueString}.example" should no longer appear in the blocked domains list
    And the user log should contain:
      | domain                              | event type   | action |
      | qa-block-del-{uniqueString}.example | User Blocked | Delete |

  # Names under the reserved .example TLD also get NXDOMAIN from ordinary recursion, so the DNS
  # lookup alone cannot tell an RPZ block from "name does not exist"; the zone-level assertion
  # (the rule bind serves for "<domain>.pplnbl.host.ppln") is the discriminating check.
  @regression @positive @security @critical @serial-settings
  Scenario: Applying changes actually blocks the domain at the DNS level
    Given I add the blocked domain "qa-block-dns-{uniqueString}.example"
    When I click "Apply Changes"
    Then its status should be "Active"
    And a DNS lookup for "qa-block-dns-{uniqueString}.example" should return NXDOMAIN
    And the RPZ zone "pplnbl.host.ppln" should map "qa-block-dns-{uniqueString}.example" to "."

  @regression @positive @security @serial-settings
  Scenario: Subdomains of a blocked domain are blocked too, look-alike domains are not
    Given I add the blocked domain "qa-wild-{uniqueString}.example"
    When I click "Apply Changes"
    Then the RPZ zone "pplnbl.host.ppln" should map "qa-wild-{uniqueString}.example" to "."
    And the RPZ zone "pplnbl.host.ppln" should map "*.qa-wild-{uniqueString}.example" to "."
    And the RPZ zone "pplnbl.host.ppln" should have no rule for "qa-wild-{uniqueString}x.example"

  @regression @positive @serial-settings
  Scenario: The Status column moves from Inactive to Active only after Apply Changes
    When I add the blocked domain "qa-status-{uniqueString}.example"
    Then its status should be "Inactive"
    When I reload the blocked domains page
    Then its status should be "Inactive"
    When I click "Apply Changes"
    Then its status should be "Active"

  # 10 per page ordered by create_at descending; other scenarios add rows in parallel, so order is
  # asserted on the "Creation Date" column, never on which domain comes first.
  @regression @positive
  Scenario: Paginating the blocked domains list shows ten rows per page, newest first
    Given more than 10 blocked domains exist
    When I reload the blocked domains page
    Then the blocked domains list should show 10 rows
    And the blocked domains list should be ordered by "Creation Date" newest first
    When I go to page 2 of the blocked domains list
    Then the blocked domains pager should show page 2 as current
