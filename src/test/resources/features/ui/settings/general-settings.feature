@ui @settings @general-settings @serial-settings
Feature: General settings
  An administrator configures the default language/timezone, the per-category RPZ action
  (Alert / Block / Off) and the DNS Access Control networks.

  # Source facts (docs/threatidr-source-facts.md section 10a, views/setting/views.py:321-526):
  #  - default_lang choices are en and ja only; default_timezone must be in pytz.common_timezones.
  #    The language change applies in-process immediately; the timezone only after an app restart.
  #  - RPZ save: JSON {"threats_categories": {"<Category>": "alert"|"block"|"off"}}, writes
  #    /etc/named.conf and runs rndc reconfig. User_Allowed is fixed to alert, User_Blocked to block.
  #  - Access Control: each entry must be IPv4 without host bits, IPv6, or "localhost";
  #    otherwise "Invalid organization network address found."; empty list -> "Invalid organization
  #    network found." (both come back with a trailing space; the steps compare trimmed text)
  #  - UI labels (templates/setting/partials/alert_settings/general.html): language options "EN" /
  #    "JA" (forms.py:87-95); RPZ actions "Alert" / "Block" / "Off"; RPZ save asks "You are about to
  #    save the rpz settings. This may affect the system. Do you want to proceed?" and then shows
  #    "Successfully saved Policy Settings"; Access Control inputs "Enter IP to Listen DNS Requests"
  #    and "Enter IP to Allow DNS Queries"; category names come from THREATS_CATEGORIES
  #    (core/settings.py:123-138)
  #
  # ISOLATION (@serial-settings): every scenario here changes global state. A language change
  # switches every request to that locale; an Access Control save rewrites allow-query and can lock
  # DnsClient (127.0.0.1 via the docker port) out of bind9; an RPZ save changes DNS for everyone.
  # Each scenario captures the original values first and restores them in cleanup.

  Background:
    Given I am logged in as "admin"
    And the original general settings are recorded for restore
    And I am on the "General Settings" settings tab

  @smoke @positive
  Scenario: Changing the default language and timezone persists
    When I set the default language to "JA" and timezone to "Asia/Tokyo"
    And I click "Save"
    Then reloading the settings page should show "JA" and "Asia/Tokyo"

  # LANGUAGES also contains "bn" (core/settings.py:53-57) but the form offers en and ja only.
  @regression @positive
  Scenario: Only English and Japanese are offered as the default language
    Then the default language options should be exactly "EN" and "JA"

  # NOT RUN - would lock the test host out of DNS (decided 2026-10-19): every RPZ save and every
  # Access Control save that passes validation regenerates config/bind9/named.conf with
  # "allow-query { localhost; <networks>; }" and runs rndc reconfig. The deployment's named.conf has
  # a hand-edited "allow-query { any; }" that the app cannot write back, and the test host reaches
  # bind9 through the docker port (gateway source address, not in the stored networks), so DnsClient
  # would get REFUSED for every later RPZ check, with no way to undo it from the UI. Also: no
  # regular category's <select> marks its stored action as selected (only User Allowed / User
  # Blocked do), so the page always shows "Alert" and a UI save would send "alert" for every
  # category. Needs a disposable bind9 / named.conf before it can run.
  @regression @positive @wip
  Scenario Outline: An RPZ category's action can be changed and persists
    When I set the "<category>" RPZ action to "<action>"
    And I save the RPZ zone settings and confirm the prompt
    Then I should see the RPZ settings message "Successfully saved Policy Settings"
    And reloading the settings page should show "<category>" set to "<action>"

    Examples:
      | category | action |
      | Botnet   | Block  |
      | Phishing | Off    |
      | Adware   | Alert  |

  @regression @positive
  Scenario: The User Allowed and User Blocked RPZ actions are fixed
    Then the "User Allowed" RPZ action should be "Alert" and not editable
    And the "User Blocked" RPZ action should be "Block" and not editable

  # NOT RUN - would lock the test host out of DNS (decided 2026-10-19): every RPZ save and every
  # Access Control save that passes validation regenerates config/bind9/named.conf with
  # "allow-query { localhost; <networks>; }" and runs rndc reconfig. The deployment's named.conf has
  # a hand-edited "allow-query { any; }" that the app cannot write back, and the test host reaches
  # bind9 through the docker port (gateway source address, not in the stored networks), so DnsClient
  # would get REFUSED for every later RPZ check, with no way to undo it from the UI. Also: no
  # regular category's <select> marks its stored action as selected (only User Allowed / User
  # Blocked do), so the page always shows "Alert" and a UI save would send "alert" for every
  # category. Needs a disposable bind9 / named.conf before it can run.
  @regression @positive @wip
  Scenario: Adding and removing multiple networks in Access Control persists
    When I add "10.0.0.0/8" and "192.168.1.0/24" to "Allow DNS Queries"
    And I remove "10.0.0.0/8" from "Allow DNS Queries"
    And I save Access Control
    Then reloading the settings page should show "192.168.1.0/24" under "Allow DNS Queries"
    And "10.0.0.0/8" should not be shown under "Allow DNS Queries"
    And a DNS lookup from the test host should still be answered

  # Read live before implementing: Tagify may refuse some of these values client-side before the
  # save; the server-side message is the one asserted.
  @regression @negative
  Scenario Outline: An invalid network in Access Control is rejected
    When I add "<network>" to "Allow DNS Queries"
    And I save Access Control
    Then I should see the access control error "Invalid organization network address found."

    Examples:
      | network         |
      | 999.999.999.999 |
      | 10.0.0.1/8      |
      | not-an-ip       |

  # Known defect (observed 2026-10-19): with every tag removed, the hidden original input (required)
  # is empty, so the browser's constraint validation silently blocks the submit: no request is
  # sent and the user sees no message at all. The server-side message is never reachable from the
  # page.
  @regression @negative @known-defect
  Scenario: BUG - Saving Access Control with every network removed is rejected with a message
    When I remove every network from "Allow DNS Queries"
    And I save Access Control
    Then I should see the access control error "Invalid organization network found."

  # Known defect: "localhost" is accepted by Access Control (views/setting/views.py:439-526) but the
  # next RPZ save re-validates the stored networks without the localhost exception and returns 400
  # "Invalid organization network address found." (:345-436).
  # NOT RUN - would lock the test host out of DNS (decided 2026-10-19): every RPZ save and every
  # Access Control save that passes validation regenerates config/bind9/named.conf with
  # "allow-query { localhost; <networks>; }" and runs rndc reconfig. The deployment's named.conf has
  # a hand-edited "allow-query { any; }" that the app cannot write back, and the test host reaches
  # bind9 through the docker port (gateway source address, not in the stored networks), so DnsClient
  # would get REFUSED for every later RPZ check, with no way to undo it from the UI. Also: no
  # regular category's <select> marks its stored action as selected (only User Allowed / User
  # Blocked do), so the page always shows "Alert" and a UI save would send "alert" for every
  # category. Needs a disposable bind9 / named.conf before it can run.
  @regression @negative @wip @known-defect
  Scenario: BUG - after saving "localhost" in Access Control, the RPZ settings can still be saved
    When I add "localhost" to "Allow DNS Queries"
    And I save Access Control
    Then reloading the settings page should show "localhost" under "Allow DNS Queries"
    When I save the RPZ zone settings and confirm the prompt
    Then I should see the RPZ settings message "Successfully saved Policy Settings"

  # Needs outbound internet to be blocked for the app container, which this shared environment
  # cannot do. The page calls https://ifconfig.me on every load (views/setting/views.py:209) and
  # shows "127.0.0.1/32, <public ip>/32" as the default networks.
  @regression @negative @blocked-env @wip
  Scenario: The settings page still loads when the public IP cannot be detected
    Given the app cannot reach "https://ifconfig.me"
    When I am on the "General Settings" settings tab
    Then the page should load without a server error
    And the default network list should contain "127.0.0.1/32"
