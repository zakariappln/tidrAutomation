@ui @settings @syslog @dns-bypass @serial-settings
Feature: Syslog forwarding and DNS bypass settings
  An administrator configures a syslog forwarding target and the DNS-bypass protection that
  blocks well-known "domain to IP" lookup sites.

  # Source facts (views/setting/views.py:1217-1272, templates/setting/partials/alert_settings/
  # syslog.html):
  #  - the form lives on the "System Log" settings tab. The page sends JSON {ip, port:
  #    Number(<input type=number>), is_forwarding, socktype}. Server order: missing ip or
  #    is_forwarding -> 409 "Invalid Input"; a falsy port becomes 514; bad ip -> "Invalid IP, Enter
  #    a Valid IP"; port not in range(65536) -> "Invalid Port"; then a live connection test (TCP when
  #    the "Syslog Transmission Over TCP" box is ticked, else UDP) -> "Connection refused when trying
  #    to connect to destination IP." etc. All errors are HTTP 409 and shown as a toast.
  #  - port 0 or an empty/non-numeric port arrives as 0 and is silently replaced by 514, so the
  #    fact-sheet item "port 0 is accepted" cannot happen; no scenario asserts it.
  #  - SAFETY: a save that passes the connection test is STORED and broadcasts a Celery worker
  #    shutdown (:1256-1259). A UDP send to 127.0.0.1 passes, so every negative row below must fail
  #    before the connection test or use TCP to a closed port. Never submit an empty port over UDP.
  #  - POST /settings/dns-bypass {"status":"Enable"} writes zone block_domain_to_ip.host.dtq with
  #    "CNAME ." for ip-api.com, domaintoipconverter.com, nslookup.io, dnschecker.org, infobyip.com,
  #    reverseip.domaintools.com. The "Detect Dns Bypass" tab is commented out, so the step posts
  #    the request over the admin session. The generated named.conf does not list that zone in
  #    response-policy (core/utils.py:405-424) but includes /etc/named.rfc1912.zones, so whether
  #    the bypass takes effect is UNCLEAR (fact sheet "Unclear items"): not tagged @known-defect.
  #  - GET /settings/remove-syslog-info deletes the syslog config on a GET (any logged-in user).
  #
  # The syslog connection test runs from the server: only 127.0.0.1 targets are used here, never a
  # real internal host. Every scenario restores the original syslog and bypass settings.

  Background:
    Given I am logged in as "admin"
    And the original syslog and DNS bypass settings are recorded for restore
    And I am on the "System Log" settings tab

  # Needs a disposable syslog listener reachable from the app container; a successful save also
  # shuts the Celery workers down.
  @regression @positive @blocked-env @wip
  Scenario: Saving a valid syslog target against a reachable test listener succeeds
    Given a disposable test listener is reachable at a known host and port
    When I set the syslog target to that host and port
    And I click "Save"
    Then I should see the syslog message "Configuration set successfully"

  @regression @negative
  Scenario Outline: An invalid syslog port or IP is rejected
    When I save the syslog target "<ip>" port "<port>" over "TCP"
    Then I should see the syslog message "<error>"

    Examples:
      | ip        | port  | error                        |
      | 127.0.0.1 | 70000 | Invalid Port                 |
      | 127.0.0.1 | -1    | Invalid Port                 |
      | 300.1.1.1 | 514   | Invalid IP, Enter a Valid IP |
      | not-an-ip | 514   | Invalid IP, Enter a Valid IP |

  @regression @negative
  Scenario: An unreachable syslog target is reported, not saved silently
    When I save the syslog target "127.0.0.1" port "9" over "TCP"
    Then I should see the syslog message "Connection refused when trying to connect to destination IP."
    And reloading the settings page should not show "127.0.0.1" port "9" as the syslog target

  # Known defect (observed 2026-10-19): after "Enable" the app writes block_domain_to_ip.host.dtq,
  # but bind9 never serves that zone (no zone statement in named.conf and not in response-policy;
  # an SOA query for it is NXDOMAIN from the root), so ip-api.com still resolves (208.95.112.1).
  @regression @positive @known-defect
  Scenario: BUG - Enabling DNS Bypass blocks the known domain-to-IP lookup sites
    When I enable DNS Bypass
    Then a DNS lookup for "ip-api.com" should return NXDOMAIN
    And a DNS lookup for "nslookup.io" should return NXDOMAIN
    When I disable DNS Bypass
    Then a DNS lookup for "ip-api.com" should not return NXDOMAIN
