# Project Structure

Single Maven module. Framework code (reusable, no `@Test`) lives in `src/main/java`; tests and test-only resources live in `src/test/java` / `src/test/resources`. Replace `com.company.automation` with the user's base package.

```
<artifactId>/
├── pom.xml
├── testng.xml                      # UI regression suite, group-based
├── testng-api.xml                  # API suite
├── README.md
├── .gitignore
├── .editorconfig
├── .github/workflows/ci.yml
│
├── src/main/java/com/company/automation/
│   ├── core/                       # shared by UI and API — the ONLY cross-layer package
│   │   ├── config/
│   │   │   ├── ConfigManager.java          # loads config/<env>.properties + -D overrides
│   │   │   ├── FrameworkConfig.java        # record view of the config
│   │   │   └── Environment.java            # enum QA, STAGING, PROD
│   │   ├── listeners/
│   │   │   ├── TestListener.java           # ITestListener: attach artifacts on failure
│   │   │   ├── SuiteListener.java          # ISuiteListener: write allure environment.properties
│   │   │   ├── RetryAnalyzer.java          # IRetryAnalyzer, bounded by config
│   │   │   └── AnnotationTransformer.java  # applies RetryAnalyzer to every @Test
│   │   ├── reporting/
│   │   │   └── AllureAttachments.java      # screenshot/trace/video/json helpers
│   │   ├── data/
│   │   │   ├── TestDataLoader.java         # JSON → record via Jackson
│   │   │   └── Faker*.java                 # thin wrappers if datafaker is used
│   │   └── utils/
│   │       ├── JsonUtils.java
│   │       └── DateUtils.java
│   │
│   ├── ui/                         # browser automation only
│   │   ├── driver/
│   │   │   ├── PlaywrightFactory.java      # ThreadLocal<Playwright/Browser/Context/Page>
│   │   │   ├── BrowserType.java            # enum CHROMIUM, FIREFOX, WEBKIT, CHROME, EDGE
│   │   │   └── ContextOptionsBuilder.java  # viewport, locale, storageState, tracing, video
│   │   ├── pages/
│   │   │   ├── BasePage.java
│   │   │   ├── components/                 # reusable widgets: Header, Modal, DataTable
│   │   │   └── <area>/                     # e.g. auth/LoginPage.java, checkout/CartPage.java
│   │   └── auth/
│   │       └── StorageStateManager.java    # login once, persist storageState per role
│   │
│   └── api/                        # HTTP automation only, built on Playwright APIRequestContext
│       ├── client/
│       │   ├── ApiClient.java              # base: request context, logging, Allure attach
│       │   ├── ApiResponse.java            # record(status, headers, body, T parsed)
│       │   └── RequestSpec.java            # record builder for headers/query/body
│       ├── clients/
│       │   └── <Resource>Client.java       # UsersClient, OrdersClient …
│       ├── models/
│       │   ├── request/                    # records for request payloads
│       │   └── response/                   # records for response payloads
│       └── auth/
│           └── TokenProvider.java          # OAuth/JWT acquisition + caching
│
├── src/test/java/com/company/automation/
│   ├── ui/
│   │   ├── base/BaseUiTest.java            # @BeforeMethod page, @AfterMethod cleanup
│   │   └── tests/<area>/<Name>Test.java
│   └── api/
│       ├── base/BaseApiTest.java           # @BeforeClass request context
│       └── tests/<Resource>Test.java
│
└── src/test/resources/
    ├── config/
    │   ├── default.properties              # committed defaults
    │   ├── qa.properties
    │   └── staging.properties
    ├── testdata/
    │   ├── ui/                             # JSON fixtures for UI tests
    │   └── api/                            # JSON payloads + JSON schemas
    ├── schemas/                            # JSON Schema files for API contract checks
    ├── allure.properties
    ├── categories.json
    └── logback-test.xml
```

## Naming conventions

| Thing            | Convention                                 | Example                        |
|------------------|--------------------------------------------|--------------------------------|
| Page object      | `<Screen>Page`                             | `LoginPage`, `OrderDetailsPage`|
| Component        | `<Widget>Component`                        | `HeaderComponent`              |
| API client       | `<Resource>Client` (plural resource)       | `UsersClient`                  |
| Request record   | `<Action><Resource>Request`                | `CreateUserRequest`            |
| Response record  | `<Resource>Response`                       | `UserResponse`                 |
| UI test class    | `<Feature>Test` in `ui/tests/<area>/`      | `LoginTest`                    |
| API test class   | `<Resource>ApiTest` in `api/tests/`        | `UsersApiTest`                 |
| Test method      | `should<ExpectedBehaviour>When<Condition>` | `shouldShowErrorWhenPasswordIsWrong` |
| Locator field    | camelCase noun, no `Btn`/`Txt` suffixes    | `submitButton`, `emailInput`   |
| Groups           | lowercase, hyphen-free                     | `smoke`, `regression`, `checkout` |

## Dependency direction

```
tests (src/test) ──► ui / api (src/main) ──► core (src/main)
                     ui ✗──► api          api ✗──► ui
```

Enforce with a review rule: any `import com.company.automation.ui.*` inside `api/**` (or the reverse) is a violation. If a test genuinely needs both (e.g. create data via API, verify in UI), that is a **hybrid test**: place it under `ui/tests/<area>/`, extend `BaseUiTest`, and obtain the API client through `BaseUiTest#api()` which wraps `page.request()` / a dedicated `APIRequestContext`. The framework layers still stay separate.

## Resources and config precedence

1. `-D` system property (e.g. `-Dbase.url=...`)
2. Environment variable (uppercase, dots → underscores: `BASE_URL`)
3. `config/<env>.properties` where `env` = `-Denv=qa` (default `qa`)
4. `config/default.properties`

Keys (fixed vocabulary):

```
env=qa
base.url=https://qa.example.com
api.base.url=https://api.qa.example.com
browser=chromium            # chromium | firefox | webkit | chrome | msedge
headless=true
slow.mo.ms=0
viewport.width=1440
viewport.height=900
locale=en-US
timeout.action.ms=10000
timeout.navigation.ms=30000
timeout.expect.ms=7000
trace.mode=on-failure       # off | on | on-failure | retain-on-failure
video.mode=on-failure
screenshot.mode=on-failure
retry.count=1
api.timeout.ms=15000
auth.username=              # supplied via env var AUTH_USERNAME
auth.password=              # supplied via env var AUTH_PASSWORD
```
