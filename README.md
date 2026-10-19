# PPLN Automation (UI + API)

Java 25 · Playwright for Java · TestNG · Cucumber-TestNG · Allure. Browser and REST automation for the
PPLN application in one Maven module, two test styles:

- **Gherkin** (`src/test/resources/features/**`) executed by two Cucumber-TestNG runners.
- **Plain TestNG** classes extending `BaseUiTest` / `BaseApiTest`.

Both styles run from the same suite files and land in the same Allure report.

## Prerequisites

- JDK 25, Maven 3.8+
- Browsers: `mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps"`
- Environment variables (never committed):

| Variable | Purpose |
|----------|---------|
| `AUTH_USERNAME` / `AUTH_PASSWORD` | default credentials (role `default`) |
| `AUTH_<ROLE>_USERNAME` / `AUTH_<ROLE>_PASSWORD` | optional per-role credentials, e.g. `AUTH_ADMIN_USERNAME`; roles without their own pair fall back to the default pair |
| `BASE_URL`, `API_BASE_URL`, `AUTH_BASE_URL` | target hosts (or `-Dbase.url=`, `-Dapi.base.url=`, `-Dauth.base.url=`) |
| `AUTH_EXPIRED_TOKEN` | a known-expired JWT for negative scenarios |
| `TEST_ENV` | alternative to `-Denv=` |
| `PROD_CONFIRM=true` | required (or `-Dprod.confirm=true`) before anything runs against `prod` |

Per-developer values can also live in `config/<env>.local.properties` (git-ignored; see
`config/staging.local.properties.example`).

## Run commands

| Goal | Command |
|------|---------|
| UI smoke (TestNG groups + Gherkin tags) | `mvn clean test -Dsuite=testng.xml -Dgroups=smoke` |
| UI regression | `mvn clean test -Dsuite=testng.xml` |
| UI by area | `mvn clean test -Dsuite=testng.xml -Dgroups=login` |
| API suite | `mvn clean test -Dsuite=testng-api.xml` |
| API smoke | `mvn clean test -Dsuite=testng-api.xml -Dgroups=smoke` |
| Glue gate (0 undefined / ambiguous steps) | `mvn clean test -Dsuite=testng-api.xml -Dcucumber.execution.dry-run=true`<br>`mvn clean test -Dsuite=testng.xml -Dcucumber.execution.dry-run=true` |
| Other env / browser / headed | `-Denv=uat -Dbrowser=firefox -Dheadless=false -Dslow.mo.ms=300` |
| Production sanity (guarded) | `mvn clean test -Dsuite=testng-api.xml -Denv=prod -Dgroups=sanity -Dprod.confirm=true` |
| Report | `mvn allure:serve` (or `./report.sh` to serve a static report on the LAN) |

`-Dgroups=a,b` filters plain TestNG tests by group and, through the runners, Cucumber scenarios by tag
`(@a or @b) and not @wip`. An explicit `-Dcucumber.filter.tags="..."` overrides that mapping.
Profiles `-Pui` / `-Papi` are shorthands for `-Dsuite=`; `-Pci` enables `-Xlint:all -Werror` and Spotless.

## Configuration: per environment

Every run targets exactly one **environment**: `staging`, `uat` or `prod`. Files are layered, later
ones overriding earlier ones:

```
config/default.properties            every key with a safe default (browser, timeouts, artifacts, env=staging)
config/<env>.properties              that environment's UI / API / auth hosts (required)
config/<env>.local.properties        optional, git-ignored: your credentials and overrides
```

Per key precedence at runtime: `-Dkey` > env var `KEY` (dots to underscores, upper-case, e.g.
`API_BASE_URL`) > `<env>.local.properties` > `<env>.properties` > `default.properties`. Select with
`-Denv=uat` (or `TEST_ENV=uat`). A missing `config/<env>.properties` fails fast with the path to
create; `env=prod` refuses to start without `-Dprod.confirm=true`. Committed files contain
placeholders (`REPLACE-ME-*.invalid`) only.

To add an environment: add its value to `core/config/Environment` and create `config/<env>.properties`.

Keys: `env`, `base.url`, `api.base.url`, `browser`, `headless`, `slow.mo.ms`, `viewport.width`,
`viewport.height`, `locale`, `timeout.action.ms`, `timeout.navigation.ms`, `timeout.expect.ms`,
`trace.mode`, `video.mode`, `screenshot.mode`, `retry.count`, `api.timeout.ms`, `auth.username`,
`auth.password`, `auth.base.url`, `auth.login.path`, `auth.token.json.pointer`, `auth.expired.token`,
`auth.state.dir`, `auth.state.max.age.minutes`, `auth.state.refresh.minutes`, `soft.checks`, `dns.host`, `dns.port`, `dns.timeout.ms`.

## Structure

```
testng.xml / testng-api.xml                 root suites (UI: parallel=methods; API: parallel=classes)
src/main/java/com/ppln/automation/
  core/config        ConfigManager, FrameworkConfig, Environment, ArtifactMode, Credentials
  core/listeners     TestListener, SuiteListener, RetryAnalyzer, AnnotationTransformer
  core/reporting     AllureAttachments (masked)
  core/data          TestDataLoader
  core/utils         JsonUtils, DateUtils, Secrets, JsRegex
  ui/driver          PlaywrightFactory (ThreadLocal), BrowserKind, ContextOptionsBuilder
  ui/pages           BasePage, components/BaseComponent, auth/LoginPage, dashboard/DashboardPage (provisional locators)
  ui/auth            StorageStateManager (login once per role -> .auth-state/<env>/<role>.json, reused across runs), LoginRateLimitGuard
  api/client         ApiClient (APIRequestContext + Allure cURL/request/response attachments), RawApiClient,
                     ApiResponse, RequestSpec, MultipartField, CurlCommand, Envelope, Paged
  api/clients        <Resource>Client per resource (empty: add as endpoints are automated)
  api/models         request/LoginRequest, response/<Resource>Response (empty)
  api/auth           TokenProvider, AuthHeaders, LoginRequestEncoder (+ Plaintext default)
src/test/java/com/ppln/automation/
  core/bdd           PlaceholderResolver, DataTables, CucumberTagFilter (shared by UI and API glue)
  ui/base            BaseUiTest            ui/tests/<area>/<Feature>Test (plain TestNG)
  ui/bdd             RunUiCucumberTest, context/{UiScenarioContext, SoftChecks}, steps/{Hooks, AuthSteps}
  api/base           BaseApiTest           api/tests/<Resource>ApiTest (plain TestNG)
  api/bdd            RunApiCucumberTest, context/{ScenarioContext, ExpectedValueMatcher, SchemaRegistry, MultipartFixtures},
                     steps/{Hooks, RequestSteps, ResponseSteps, VariableSteps}
src/test/resources/
  config/            default.properties, {staging,uat,prod}.properties, staging.local.properties.example
  features/ui/**     features/api/<resource>/*.feature
  schemas/           envelope.schema.json, paged-envelope.schema.json (+ <resource>/*.schema.json)
  testdata/          api/, ui/, multipart/, files/
  cucumber.properties, allure.properties, categories.json, logback-test.xml
```

Dependency direction: `tests -> ui | api -> core`. `api/**` never imports `ui/**` and vice versa.

## Conventions

- Page objects: one screen per class, `private final Locator` fields, `@Step` on every public action,
  return `this` or the next page, **no assertions, no waits**. Steps/tests assert with
  `PlaywrightAssertions.assertThat` (DOM) and AssertJ (values).
- API clients: one resource per class, one method per endpoint, typed `ApiResponse<Envelope<T>>`,
  records for DTOs, **no assertions**.
- Groups / tags (fixed vocabulary): `smoke`, `regression`, `sanity`, `<area>`, `api`, `wip`; API
  features add `@<resource>`, `@get|post|put|patch|delete`, `@positive|negative|security|contract`.
  UI scenarios that submit the real login form add `@serial-auth` (one login slot of the app's 3/minute
  limit); the ones that deliberately exhaust the limit add `@login-window` too. See `LoginRateLimitGuard`.
- Allure: `@Epic` + `@Feature` on classes, `@Story` + `@Severity` on methods; features/scenarios map
  automatically. Failures attach screenshot + trace + video (UI) or request + response (API); secrets
  are masked by `Secrets.mask`.
- Scenario state is injected by PicoContainer (`ScenarioContext`, `UiScenarioContext`); never static.
  Every data-creating step registers `ctx.onCleanup(...)`; hooks run cleanups LIFO and never throw.
- No `Thread.sleep`, no `System.out`, no hard-coded hosts or credentials.
- Traces: open `target/traces/*.zip` (also attached to Allure) at https://trace.playwright.dev.

## Adding tests

| Need | Do |
|------|----|
| New API endpoint | client method in `api/clients/<Resource>Client` + records in `api/models` -> feature in `features/api/<resource>/` -> precondition steps (`Given a <resource> exists with:` + cleanup) in `api/bdd/steps/PreconditionSteps` -> `schemas/<resource>/*.schema.json` |
| New UI flow | page objects in `ui/pages/<area>/` -> feature in `features/ui/<area>/` -> steps by responsibility in `ui/bdd/steps/` |
| Plain TestNG test | `ui/tests/<area>/<Feature>Test extends BaseUiTest` or `api/tests/<Resource>ApiTest extends BaseApiTest`; picked up by the suite XMLs via `<packages>` |
| Authenticated UI test | override `storageState()` to `StorageStateManager.forRole("admin")` (plain) or use `Given I am logged in as "admin"` (BDD). One real login per role; the session is saved to `.auth-state/<env>/<role>.json` (git-ignored) and reused by later runs while younger than `auth.state.max.age.minutes` and still accepted by the app (checked with one HTTP request). If a page opened with it shows the login form (logout elsewhere, server restart, expiry), the step logs in again and re-saves the file. Delete the file or call `StorageStateManager.invalidate(role)` to force a login. |

Placeholders available in features: `{baseUrl}`, `{uiBaseUrl}`, `{now}`, `{now+1d}`, `{uniqueEmail}`,
`{uniqueString}`, `{uuid}`, `{string of N chars}`, plus any stored variable (`store "$.data.id" as "recordId"`).

API step vocabulary already implemented (`api/bdd/steps`): `the request is authenticated as {string}`,
`the request is not authenticated`, `the request uses an expired token`, `the request has headers:`,
`the request has query parameters:`, `the request body is:`, `I prepare a multipart request with the form fields`,
`I send a {word} request to {string}`, `the response status is {int}`, `the response headers contain:`,
`the response body matches schema {string}`, `the response body has:`, `the response body does not contain {string}`,
`the response time is under {int} ms`, `{string} is stored as {string}`, `store {string} as {string}`,
`store header {string} as {string}`.

## Auth hook points (to complete before the first real run)

1. **Login request encoding** - `api/auth/LoginRequestEncoder`. The default
   `PlaintextLoginRequestEncoder` sends `{ "username", "password" }` verbatim. If the backend expects an
   encrypted or differently shaped payload, implement the interface and register the class in
   `src/test/resources/META-INF/services/com.ppln.automation.api.auth.LoginRequestEncoder`
   (marker: `TODO [AUTH-ENCODING]`).
2. **Login endpoint and token location** - `auth.login.path` and `auth.token.json.pointer` in
   `config/<env>.properties` are placeholders; confirm against Swagger.
3. **Hosts** - replace `REPLACE-ME-*.invalid` in `config/<env>.properties`, or via env vars
   (`BASE_URL`, `API_BASE_URL`, `AUTH_BASE_URL`) / `-D` switches.
4. **UI locators** - `LoginPage` / `DashboardPage` locators are provisional; verify with Playwright MCP
   (bdd-step-writer agent) against the live app. The login scenarios are tagged `@wip` until then.
5. **Response envelope** - `Envelope` / `Paged` and `schemas/envelope.schema.json` assume
   `{ statusCode, message, data }`; align with the real contract.
