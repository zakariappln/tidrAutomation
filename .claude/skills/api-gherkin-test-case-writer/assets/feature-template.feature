@api @<resource> @<method>
Feature: <Action> <resource> — <METHOD> <path>
  <One-line purpose.>
  Spec: <source of truth>
  Auth: <scheme> — <allowed roles> allowed, <denied roles> denied
  Success: <code> [+ headers]
  Errors: <code condition>, <code condition>

  # ASSUMPTIONS (confirm or correct):
  #  - <none | list>

  Background:
    Given the API base URL is "{baseUrl}"
    And the request has headers:
      | name         | value            |
      | Content-Type | application/json |
      | Accept       | application/json |

  @smoke @positive @cleanup
  Scenario: Returns <code> and <outcome> when <condition>
    Given the request is authenticated as "<role>"
    # precondition for dependent values, if any:
    # And a <parent> exists with:
    #   | <field> | <value> |
    # And store "$.id" as "<parentId>"
    And the request body is:
      """json
      { }
      """
    When I send a <METHOD> request to "<path>"
    Then the response status is <code>
    And the response headers contain:
      | name         | value            |
      | Content-Type | application/json |
    And the response body matches schema "schemas/<resource>.schema.json"
    And the response body has:
      | path | expected  |
      | $.id | is a UUID |
    But the response body does not contain "<sensitiveField>"
    And store "$.id" as "<resourceId>"
    And the created <resource> {<resourceId>} is deleted

  @regression @negative
  Scenario Outline: Returns 400 when <field> is <problem>
    Given the request is authenticated as "<role>"
    And the request body is:
      """json
      { "<field1>": "<field1>", "<field2>": "<field2>" }
      """
    When I send a <METHOD> request to "<path>"
    Then the response status is 400
    And the response headers contain:
      | name         | value              |
      | Content-Type | <errorContentType> |
    And the response body has:
      | path              | expected    |
      | $.errors[0].field | <field>     |
      | $.errors[0].code  | <errorCode> |

    Examples: Missing required fields
      | field | problem | field1 | field2 | errorCode |
      |       |         |        |        |           |

  @regression @security
  Scenario Outline: Returns <status> when the caller is <caller>
    Given the request is <authState>
    When I send a <METHOD> request to "<path>"
    Then the response status is <status>
    And the response headers contain:
      | name         | value              |
      | Content-Type | <errorContentType> |

    Examples:
      | caller          | authState                 | status |
      | unauthenticated | not authenticated         | 401    |
      | <deniedRole>    | authenticated as "<role>" | 403    |
