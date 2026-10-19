@ui @reports
Feature: Reports
  An administrator generates summary (PDF) or CSV reports of DNS/threat activity for a date range.

  # Source facts (docs/threatidr-source-facts.md section 8, views/report/views.py,
  # templates/report/index.html):
  #  - the page has quick ranges Today / Yesterday / Last 7 Day / Last 30 Day, From Date, To Date,
  #    "Report Format" with Summary and CSV (views/report/forms.py:23-28; "Details" is commented out),
  #    a "Generate" button, and a "View" link that simply opens Threat Detection (/threats/), which
  #    marks every threat viewed - so no scenario clicks "View"
  #  - Generate with Summary -> POST /reports/pdf_download {"lang","range","type":"summary"} which
  #    writes static/reports/<MonDDYYYY>_<MonDDYYYY>_summary_report.pdf and answers {"data": url};
  #    with CSV -> POST /reports/csv_download returning JSON rows with keys Timestamp, Country
  #    Destination, Threat Type, Source IP, Destination Domain, Destination IP, Tld, Asn, Operator,
  #    Risk, Risk Score (Info 0, Gray 1, Low 2, High 3, Critical 4)
  #  - csv_download and pdf_download are login-only: "report-export" is never enforced (defect)
  #  - FAQ text: "We will preserve all of your prior data, even though statistical data is only
  #    available for 30 days" (templates/report/index.html:170)
  #
  # ASSUMPTIONS (confirm or correct):
  #  - CSV Asn/Operator columns may always be empty due to a geoip_city parsing bug seen in source
  #    review (json.loads() on an already-parsed dict, silently caught) - the completeness scenario
  #    below should stay in the suite to catch this either way, not be treated as "obviously true"
  #  - PDF generation depends on wkhtmltopdf + CJK fonts being present in the app image

  Background:
    Given I am logged in as "admin"
    And I am on the reports page

  # Offsets below are what the app does today (observed live 2026-10-17), NOT confirmed as intended
  # - ask the product owner: "Yesterday" ends TODAY rather than yesterday (a two-day range), and
  # "Last 7 Day" / "Last 30 Day" go back 7 / 30 days and include today (8 / 31 days inclusive).
  # If either is a bug, change the offsets here once it's fixed; the step itself stays the same.
  @smoke @positive
  Scenario Outline: Quick-range buttons populate From/To Date correctly
    When I click the "<quick range>" quick-range button
    Then the From Date should be <from days ago> days before today
    And the To Date should be <to days ago> days before today

    Examples:
      | quick range | from days ago | to days ago |
      | Today       | 0             | 0           |
      # COMMENTED OUT 2026-10-17, pending product-owner decision: business rule not yet decided (should Yesterday end yesterday?)
      # | Yesterday   | 1             | 0           |
      | Last 7 Day  | 7             | 0           |
      | Last 30 Day | 30            | 0           |

  @regression @positive
  Scenario: Generating a CSV report includes all expected columns
    When I generate a CSV report for the last 7 days
    Then the CSV should contain the columns:
      | column              |
      | Timestamp           |
      | Country Destination |
      | Threat Type         |
      | Source IP           |
      | Destination Domain  |
      | Destination IP      |
      | Tld                 |
      | Asn                 |
      | Operator            |
      | Risk                |
      | Risk Score          |
    And every Risk Score should match its Risk (Info 0, Gray 1, Low 2, High 3, Critical 4)
    And the share of rows with an empty Asn and Operator should be attached to the report

  # Still @wip (glued, run 2026-10-19): the PDF has 8 pages and 0 font resources - wkhtmltopdf in this
  # image draws all text as vector paths, so there is no text layer to extract (pdffonts/pdftotext
  # agree). It looks right on screen but is not searchable or accessible. Product decision needed:
  # is a text layer required (then this is a BUG scenario) or not (then assert rendering instead)?
  @regression @positive @wip
  Scenario: Generating a Summary PDF report produces a readable PDF
    When I generate a "summary" PDF report for the last 7 days in language "en"
    Then the downloaded PDF should have at least one page of extractable text

  # The Japanese PDF case lives in localization/localization.feature.

  # Read live before implementing: whether the date pickers let From Date be set after To Date at
  # all, and what the page shows; no rule for this exists in source (core/utils.py:174-183 only
  # parses the dates), so only "no server error" is asserted.
  @regression @negative
  Scenario: A From Date later than the To Date does not cause a server error
    When I set the report From Date after the To Date
    And I choose the "CSV" report format
    And I click "Generate"
    Then the request should not cause a server error

  # Read live before implementing: the wording shown for an empty CSV / PDF.
  @regression @negative
  Scenario: A date range with no data does not cause a server error
    When I set the report range to next month
    And I choose the "CSV" report format
    And I click "Generate"
    Then the request should not cause a server error

  # Known defect: csv_download / pdf_download only check login, never "report-export"
  # (views/report/views.py:136, 220). The limited user holds only "Update Settings".
  @regression @negative @security @known-defect
  Scenario: BUG - a user without report permission cannot export a report directly
    Given I am logged in as a user with only the "Update Settings" permission
    When I request the CSV report download directly
    Then the request should be denied

  # Known defect: the PDF is written under static/ with a predictable name (tasks/report.py:46-50).
  # The generate step stores the returned path as the scenario variable {generatedPdfPath}.
  @regression @negative @security @known-defect
  Scenario: BUG - a generated PDF report is not downloadable without a session
    Given I generate a "summary" PDF report for the last 7 days in language "en"
    When I call "{generatedPdfPath}" without an active session
    Then the anonymous response should be a redirect to the login page

  @regression @positive
  Scenario: The data retention notice is shown
    Then I should see the text "We will preserve all of your prior data, even though statistical data is only available for 30 days"
