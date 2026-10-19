@ui @localization
Feature: Localization
  The UI is offered in English and Japanese via a language switcher; every route is namespaced
  under a /<lang_code>/ URL segment.

  # Source facts (core/settings.py:53-57, views/home/views.py:62-70, 110-115, core/app.py:95-112,
  # 157-169):
  #  - LANGUAGES = en, ja, bn; the login page offers EN and JA only
  #  - after a successful login the redirect goes to /<BABEL_DEFAULT_LOCALE>/dashboard/, so logging
  #    in from /ja/ lands on /en/ when the default is English (defect)
  #  - an unknown lang code falls back to the default locale on the home blueprint and redirects
  #    elsewhere; it must never be a 500
  #  - Japanese sidebar labels (translations/ja/LC_MESSAGES/messages.po): Dashboard ->
  #    "ダッシュボード", Settings -> "設定", Reports -> "レポート", Threat Detection -> "検知した脅威"
  #  - the English assertions assume the default language is "en" (general-settings scenarios
  #    restore it after changing it)

  @regression @positive
  Scenario: The dashboard renders in Japanese under /ja/
    Given I am logged in as "admin"
    When I navigate directly to "/ja/dashboard/"
    Then the sidebar should show the menu label "ダッシュボード"
    And the sidebar should show the menu label "設定"

  # Known defect: views/home/views.py:110-115 rebuilds the post-login URL from
  # BABEL_DEFAULT_LOCALE, discarding the /ja/ the user logged in from.
  @smoke @positive @serial-auth @known-defect
  Scenario: BUG - the chosen language persists across login
    Given I open the login page
    When I switch the language to "Japanese"
    And I log in as "admin"
    Then the URL should start with "/ja/"
    And the sidebar should show the menu label "ダッシュボード"

  @regression @negative
  Scenario Outline: An unsupported language code in the URL does not crash the app
    Given I am logged in as "admin"
    When I navigate directly to "<url>"
    Then the page should load without a server error
    And the sidebar should show the menu label "Dashboard"

    Examples:
      | url            |
      | /fr/dashboard/ |
      | /xx/           |

  # Still @wip (glued, run 2026-10-19): the PDF has no text layer at all (0 font resources; wkhtmltopdf
  # draws text as paths, the English report too - see reports.feature), so no text can be checked.
  # Rendered to an image, the Japanese headings and index entries come out BLANK (numbers and Latin
  # text render), i.e. the image lacks CJK fonts. Needs a product/env decision before it can run.
  @regression @positive @wip
  Scenario: Generating a PDF report in Japanese renders Japanese text correctly
    Given I am logged in as "admin"
    And I am on the reports page
    When I generate a "summary" PDF report for the last 7 days in language "ja"
    Then the PDF should contain Japanese characters, not missing-glyph boxes
