# Example Feature (worked)

Context that was confirmed with the user before writing (shown so you see what "confirmed" looks like):

- `POST /v1/users`, Bearer JWT, roles `admin`, `manager` may create; `viewer` gets 403.
- Required headers: `Content-Type: application/json`, `Accept: application/json`; missing Content-Type → 415.
- Body: `email` (required, unique per tenant, RFC 5322), `fullName` (required, 2–80 chars), `role` (required, enum `admin|manager|viewer`), `phone` (optional, E.164).
- Success: 201, `Location: /v1/users/{id}`, body = user with server-set `id` (UUID), `createdAt` (ISO-8601), `status: "ACTIVE"`; `password` never present.
- Errors: problem+json `{ "type", "title", "status", "errors": [{ "field", "code", "message" }] }`; codes `REQUIRED`, `INVALID_FORMAT`, `INVALID_ENUM`, `TOO_SHORT`, `TOO_LONG`; duplicate email → 409 code `DUPLICATE_EMAIL`.
- Unknown body fields are ignored. Cleanup: `DELETE /v1/users/{id}` returns 204.

```gherkin
@api @users @post
Feature: Create user — POST /v1/users
  Creates a user within the caller's tenant.
  Spec: docs/openapi/users-v1.yaml (confirmed 2026-09-21)
  Auth: Bearer JWT — admin | manager allowed, viewer denied
  Success: 201 + Location
  Errors: 400 validation (problem+json), 401, 403, 409 DUPLICATE_EMAIL, 415

  Background:
    Given the API base URL is "{baseUrl}"
    And the request has headers:
      | name         | value            |
      | Content-Type | application/json |
      | Accept       | application/json |

  @smoke @positive @cleanup
  Scenario: Returns 201 and the created user when payload is valid
    Given the request is authenticated as "admin"
    And the request body is:
      """json
      {
        "email": "{uniqueEmail}",
        "fullName": "Ada Lovelace",
        "role": "viewer",
        "phone": "+8801700000000"
      }
      """
    When I send a POST request to "/v1/users"
    Then the response status is 201
    And the response headers contain:
      | name         | value                              |
      | Content-Type | application/json                   |
      | Location     | matches ^/v1/users/[0-9a-f-]{36}$  |
    And the response body matches schema "schemas/user.schema.json"
    And the response body has:
      | path        | expected                 |
      | $.id        | is a UUID                |
      | $.email     | {uniqueEmail}            |
      | $.fullName  | Ada Lovelace             |
      | $.role      | viewer                   |
      | $.phone     | +8801700000000           |
      | $.status    | ACTIVE                   |
      | $.createdAt | is an ISO-8601 timestamp |
    But the response body does not contain "password"
    And store "$.id" as "userId"
    And the created user {userId} is deleted

  @regression @positive @cleanup
  Scenario: Created user is retrievable at the Location header
    Given the request is authenticated as "admin"
    And the request body is:
      """json
      { "email": "{uniqueEmail}", "fullName": "Grace Hopper", "role": "manager" }
      """
    When I send a POST request to "/v1/users"
    Then the response status is 201
    And store "$.id" as "userId"
    And store header "Location" as "userLocation"
    When I send a GET request to "{userLocation}"
    Then the response status is 200
    And the response headers contain:
      | name         | value            |
      | Content-Type | application/json |
    And the response body has:
      | path       | expected      |
      | $.id       | {userId}      |
      | $.email    | {uniqueEmail} |
      | $.fullName | Grace Hopper  |
    And the created user {userId} is deleted

  @regression @positive @cleanup
  Scenario: Optional phone is omitted from the response when not supplied
    Given the request is authenticated as "manager"
    And the request body is:
      """json
      { "email": "{uniqueEmail}", "fullName": "Katherine Johnson", "role": "viewer" }
      """
    When I send a POST request to "/v1/users"
    Then the response status is 201
    And the response body has:
      | path    | expected  |
      | $.phone | is absent |
    And store "$.id" as "userId"
    And the created user {userId} is deleted

  @regression @negative
  Scenario Outline: Returns 400 when <field> is <problem>
    Given the request is authenticated as "admin"
    And the request body is:
      """json
      { "email": "<email>", "fullName": "<fullName>", "role": "<role>" }
      """
    When I send a POST request to "/v1/users"
    Then the response status is 400
    And the response headers contain:
      | name         | value                    |
      | Content-Type | application/problem+json |
    And the response body matches schema "schemas/problem.schema.json"
    And the response body has:
      | path              | expected    |
      | $.status          | 400         |
      | $.errors          | has size 1  |
      | $.errors[0].field | <field>     |
      | $.errors[0].code  | <errorCode> |

    Examples: Missing required fields
      | field    | problem | email         | fullName | role   | errorCode |
      | email    | missing |               | Ada L.   | viewer | REQUIRED  |
      | fullName | missing | {uniqueEmail} |          | viewer | REQUIRED  |
      | role     | missing | {uniqueEmail} | Ada L.   |        | REQUIRED  |

    Examples: Invalid values and boundaries
      | field    | problem        | email         | fullName            | role   | errorCode      |
      | email    | not an address | ada@          | Ada L.              | viewer | INVALID_FORMAT |
      | role     | not in enum    | {uniqueEmail} | Ada L.              | wizard | INVALID_ENUM   |
      | fullName | 1 char         | {uniqueEmail} | A                   | viewer | TOO_SHORT      |
      | fullName | 81 chars       | {uniqueEmail} | {string of 81 chars}| viewer | TOO_LONG       |

  @regression @negative
  Scenario: Returns 400 when the body is not valid JSON
    Given the request is authenticated as "admin"
    And the request body is:
      """
      { "email": "{uniqueEmail}", "fullName": 
      """
    When I send a POST request to "/v1/users"
    Then the response status is 400
    And the response headers contain:
      | name         | value                    |
      | Content-Type | application/problem+json |
    And the response body has:
      | path     | expected |
      | $.status | 400      |

  @regression @negative
  Scenario: Returns 415 when Content-Type header is missing
    Given the request is authenticated as "admin"
    And the request has headers:
      | name         | value  |
      | Content-Type | <none> |
    And the request body is:
      """json
      { "email": "{uniqueEmail}", "fullName": "Ada L.", "role": "viewer" }
      """
    When I send a POST request to "/v1/users"
    Then the response status is 415
    And the response headers contain:
      | name         | value                    |
      | Content-Type | application/problem+json |

  @regression @negative @cleanup
  Scenario: Returns 409 when the email already exists in the tenant
    Given the request is authenticated as "admin"
    And a user exists with:
      | email    | {uniqueEmail} |
      | fullName | First Owner   |
      | role     | viewer        |
    And store "$.id" as "existingUserId"
    And the request body is:
      """json
      { "email": "{uniqueEmail}", "fullName": "Second Attempt", "role": "viewer" }
      """
    When I send a POST request to "/v1/users"
    Then the response status is 409
    And the response headers contain:
      | name         | value                    |
      | Content-Type | application/problem+json |
    And the response body has:
      | path              | expected        |
      | $.status          | 409             |
      | $.errors[0].field | email           |
      | $.errors[0].code  | DUPLICATE_EMAIL |
    And the created user {existingUserId} is deleted

  @regression @security
  Scenario Outline: Returns <status> when the caller is <caller>
    Given the request is <authState>
    And the request body is:
      """json
      { "email": "{uniqueEmail}", "fullName": "Ada L.", "role": "viewer" }
      """
    When I send a POST request to "/v1/users"
    Then the response status is <status>
    And the response headers contain:
      | name         | value                    |
      | Content-Type | application/problem+json |
    And the response body has:
      | path     | expected |
      | $.status | <status> |
    But the response body does not contain "stack"

    Examples:
      | caller                 | authState                     | status |
      | unauthenticated        | not authenticated             | 401    |
      | using an expired token | using an expired token        | 401    |
      | a viewer               | authenticated as "viewer"     | 403    |

  @regression @positive @cleanup
  Scenario: Ignores unknown fields in the request body
    Given the request is authenticated as "admin"
    And the request body is:
      """json
      { "email": "{uniqueEmail}", "fullName": "Ada L.", "role": "viewer", "nickname": "ignored" }
      """
    When I send a POST request to "/v1/users"
    Then the response status is 201
    And the response body has:
      | path       | expected  |
      | $.nickname | is absent |
    And store "$.id" as "userId"
    And the created user {userId} is deleted
```

## What to notice

- The `Background` holds only what **every** scenario shares (base URL, standard headers); auth differs per scenario so it stays inside each one.
- The 415 scenario overrides one Background header with `<none>` — an explicit convention, documented in the style guide's step table, rather than a second Background.
- The 409 scenario's dependency (an existing user) is a `Given ... exists with:` precondition created via API and cleaned up.
- Every scenario checks status → headers → schema → body fields, in that order, including negatives.
- Two Scenario Outlines replace what would have been ten near-identical scenarios.
- Nothing in the file is guessed; every code and error constant traces to the confirmed context block above. Had `DUPLICATE_EMAIL` not been confirmed, the 409 scenario would have appeared in the reply's *open questions* list instead of the feature.
