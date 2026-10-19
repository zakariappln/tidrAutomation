# Package Layout

Glue code is test code: it lives under `src/test/java`, beside the TestNG API tests, and depends on the framework layer (`api/**`, `core/**`) — never the reverse.

```
src/test/java/com/company/automation/api/
├── base/BaseApiTest.java                    # existing TestNG base (unchanged)
├── tests/                                   # existing plain TestNG tests (unchanged)
└── bdd/
    ├── ApiCucumberRunner.java               # extends AbstractTestNGCucumberTests
    ├── context/
    │   ├── ScenarioContext.java             # per-scenario state (PicoContainer-managed)
    │   ├── PlaceholderResolver.java
    │   ├── ExpectedValueMatcher.java
    │   └── SchemaRegistry.java
    ├── steps/
    │   ├── RequestSteps.java                # Given: headers, query, body, auth
    │   ├── ResponseSteps.java               # Then: status, headers, schema, body
    │   ├── PreconditionSteps.java           # Given: "<resource> exists with", state setup
    │   ├── VariableSteps.java               # store "$.x" as "y", store header
    │   └── Hooks.java                       # @Before / @After / @AfterStep
    └── support/
        └── DataTables.java                  # DataTable → Map helpers

src/test/resources/
├── cucumber.properties
├── features/api/<resource>/*.feature        # from api-gherkin-test-case-writer
└── schemas/*.schema.json

src/test/java/com/company/automation/api/bdd/context/   (unit tests)
├── PlaceholderResolverTest.java             # plain TestNG, group "unit"
└── ExpectedValueMatcherTest.java
```

## Dependency direction

```
bdd/steps ──► bdd/context ──► api/client, api/clients, core/config, core/reporting
bdd/steps ✗──► ui/**            (a UI step library is a separate skill)
api/**   ✗──► bdd/**            (framework never knows about Cucumber)
```

## Naming

| Thing            | Convention                                     |
|------------------|------------------------------------------------|
| Step class       | `<Responsibility>Steps`                        |
| Step method      | verb phrase mirroring the Gherkin: `theRequestHasHeaders`, `theResponseStatusIs` |
| Context key      | camelCase, same as `{placeholder}` name         |
| Resource creator | `PreconditionSteps#aUserExistsWith` → delegates to `UsersClient` |

## Adding a new resource

When features for a new resource (`orders`) arrive, you add exactly:
1. `Given an order exists with:` (+ cleanup registration) in `PreconditionSteps`.
2. `And the created order {orderId} is deleted` in `PreconditionSteps`.
3. `schemas/order.schema.json`.

Nothing else — request/response/variable steps are generic and already cover the new endpoint.
