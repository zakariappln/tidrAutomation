# Gherkin Style Guide for API Features

## File layout

```
src/test/resources/features/api/
└── users/
    ├── users-create.feature      # POST /v1/users
    ├── users-get.feature         # GET  /v1/users/{id}
    ├── users-list.feature        # GET  /v1/users
    ├── users-update.feature      # PUT/PATCH
    └── users-delete.feature
```

One endpoint + method per file by default. Small resources (≤ 6 scenarios total) may share one `users.feature`.

## Feature header

```gherkin
@api @users @post
Feature: Create user — POST /v1/users
  Creates a user in the caller's tenant.
  Spec: <link or file>            # where the facts came from
  Auth: Bearer JWT, roles admin | manager
  Success: 201 + Location header
  Errors: 400 validation, 401, 403, 409 duplicate email, 415

  # ASSUMPTIONS (confirm or correct):
  #  - none
```

The description block is the contract summary. A reviewer should be able to check the scenarios against it without opening the spec.

## Tags

| Tag family        | Values                                               | Rule                          |
|-------------------|------------------------------------------------------|-------------------------------|
| Layer             | `@api`                                               | on Feature, always            |
| Resource          | `@users`, `@orders`                                  | on Feature                    |
| Method            | `@post` `@get` `@put` `@patch` `@delete`             | on Feature                    |
| Suite             | `@smoke` \| `@regression`                            | exactly one per Scenario      |
| Category          | `@positive` `@negative` `@security` `@contract`      | exactly one per Scenario / Examples block |
| Lifecycle         | `@cleanup`                                           | scenario leaves data that a hook must delete |
| Tracking          | `@TC-123` `@JIRA-456`                                | optional, one id per tag      |
| Status            | `@wip`                                               | excluded from runs            |

Tags map 1:1 to the TestNG groups used by the framework (`api`, `smoke`, `regression`, `<resource>`, `wip`).

## Fixed step vocabulary

Reuse these phrases verbatim; consistency is what makes step definitions reusable. Add a new phrase only when none fits, and add it to this table.

### Given — context and preconditions
```gherkin
Given the API base URL is "{baseUrl}"
Given the request is authenticated as "<role>"            # resolves token from config; never inline a token
Given the request is not authenticated
Given the request uses an expired token
Given the request has headers:
  | name         | value            |
  | Content-Type | application/json |
  | Accept       | application/json |
Given the request has query parameters:
  | name | value |
  | page | 1     |
Given the request body is:
  """json
  { ... }
  """
Given a user exists with:                                  # precondition via API, not UI
  | email | {uniqueEmail} |
  | role  | viewer        |
  And store "$.id" as "userId"
Given the user {userId} has status "SUSPENDED"             # state precondition
```

### When — exactly one per scenario
```gherkin
When I send a POST request to "/v1/users"
When I send a GET request to "/v1/users/{userId}"
When I send the same POST request again with the same Idempotency-Key
```

### Then — response validation (all four surfaces, in this order)
```gherkin
Then the response status is 201
And the response headers contain:
  | name         | value                          |
  | Content-Type | application/json               |
  | Location     | matches ^/v1/users/[0-9a-f-]{36}$ |
And the response body matches schema "schemas/user.schema.json"
And the response body has:
  | path         | expected                     |
  | $.id         | is a UUID                    |
  | $.email      | {uniqueEmail}                |
  | $.role       | viewer                       |
  | $.createdAt  | is an ISO-8601 timestamp     |
  | $.password   | is absent                    |
And the response body does not contain "password"
And the response time is under 800 ms                      # only if SLA confirmed
And store "$.id" as "userId"                               # capture for later steps
```

### Cleanup
```gherkin
And the created user {userId} is deleted                   # or rely on @cleanup hook
```

## Expected-value matchers (for `expected` / `value` columns)

| Matcher                     | Meaning                                  |
|-----------------------------|------------------------------------------|
| literal                     | equals, after placeholder substitution   |
| `is a UUID`                 | format check                             |
| `is an ISO-8601 timestamp`  | format check                             |
| `is a positive integer`     | type + range                             |
| `matches <regex>`           | regex                                    |
| `is one of A, B, C`         | enum membership                          |
| `is not empty`              | non-null, non-blank                      |
| `is absent`                 | key must not exist                       |
| `is null`                   | key exists with null                     |
| `has size N`                | array length                             |
| `contains "x"`              | substring / array element                |

## Placeholders

- `{baseUrl}`, `{authToken}` — environment/config.
- `{userId}`, `{orderId}` — values stored in earlier steps of the **same** scenario (or Background).
- `{uniqueEmail}`, `{uniqueString}`, `{now}`, `{now+1d}` — generated at runtime so parallel runs never collide.
- Never put a literal id, hostname, token or real personal data in a feature file.

## Scenario Outline conventions

```gherkin
  @regression @negative
  Scenario Outline: Returns 400 when <field> is <problem>
    Given the request is authenticated as "admin"
    And the request has headers:
      | name         | value            |
      | Content-Type | application/json |
    And the request body is:
      """json
      { "email": "<email>", "fullName": "<fullName>", "role": "<role>" }
      """
    When I send a POST request to "/v1/users"
    Then the response status is 400
    And the response headers contain:
      | name         | value            |
      | Content-Type | application/problem+json |
    And the response body has:
      | path              | expected        |
      | $.errors[0].field | <field>         |
      | $.errors[0].code  | <errorCode>     |

    Examples: Missing required fields
      | field    | problem  | email            | fullName | role   | errorCode |
      | email    | missing  |                  | Ada L.   | viewer | REQUIRED  |
      | fullName | missing  | {uniqueEmail}    |          | viewer | REQUIRED  |

    Examples: Invalid formats
      | field | problem | email         | fullName | role    | errorCode      |
      | email | invalid | not-an-email  | Ada L.   | viewer  | INVALID_FORMAT |
      | role  | unknown | {uniqueEmail} | Ada L.   | wizard  | INVALID_ENUM   |
```

Rules: the title uses `<placeholders>` so each row reads as its own test; only varying data goes in columns; split `Examples:` blocks by theme and tag them when they need different suite membership; keep ≤ 10 rows per block (split otherwise).

## Writing style

- Title = outcome + condition, present tense, no "Verify that" / "Test".
- `Given` describes state, `When` one action, `Then` observable results. No `When` in `Then`, no assertions in `Given`.
- Prefer `And` over repeating `Then`. Use `But` for negative expectations (`But the response body does not contain "password"`).
- Comments (`#`) only for assumptions, spec references, or non-obvious business reasons.
- Max ~15 steps per scenario; if longer, the scenario is testing two things.
