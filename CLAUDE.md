# Test Automation — AI Engineering Workflow

Java 25 · Playwright for Java · TestNG · Cucumber-TestNG · Allure. UI and API automation for the
**PPLN** application in one repo. (Framework copied from the USBA accounting automation project and
stripped of everything accounting-specific; the structure and conventions are unchanged.)

## Skills (conventions Claude loads automatically)
| Skill | Governs |
|-------|---------|
| `playwright-java-project-architecture` | Folder layout, POM rules, ui/api separation, `testng.xml` + `testng-api.xml`, config, Allure, CI |
| `api-gherkin-test-case-writer` | How API `.feature` files are written: 4-surface validation, preconditions, Scenario Outline, ask-before-guessing |
| `api-step-definition-writer` | Cucumber glue for API features: ScenarioContext, placeholders, matchers, cleanup, runner |
| `code-quality` | Java review rules — still applies |

## Agents (delegate by task)
| Task | Agent |
|------|-------|
| Scaffold / restructure / audit the framework, add page objects, API clients, suite XMLs | `playwright-java-architect` |
| Write API test cases from a spec, curl, Postman or endpoint description | `api-gherkin-test-writer` (asks questions first — answer them before expecting features) |
| Make API features executable, add/fix API steps, hooks, runner | `api-step-definition-writer` |
| Implement or fix UI step definitions + page objects from a `.feature` (explores live app via Playwright MCP) | `bdd-step-writer` |

Pipeline for a new API endpoint: **architect** (client + models) → **gherkin writer** (features) → **step-definition writer** (glue) → run `testng-api.xml`.
Pipeline for a new UI flow: **architect** (page objects) → feature file → **bdd-step-writer** → run `testng.xml`.

## Run commands
```
mvn clean test -Dsuite=testng.xml -Dgroups=smoke        # UI smoke (TestNG + Gherkin)
mvn clean test -Dsuite=testng.xml                        # UI regression
mvn clean test -Dsuite=testng-api.xml                    # API (TestNG + Gherkin)
mvn clean test -Dsuite=testng-api.xml -Dcucumber.execution.dry-run=true   # glue gate: 0 undefined / ambiguous
mvn clean test -Dsuite=testng.xml -Dheadless=true -Dcucumber.filter.tags="@known-defect and not @wip and not @blocked-env and not @out-of-scope"   # defect report run
mvn allure:serve
```
UI tag policy and shared step vocabulary: `src/test/resources/features/ui/README.md`. Never run two test JVMs (IDE + Maven) against the app at once: the login endpoint allows 3 logins per minute per IP.
Switches: `-Denv=staging|uat|prod  -Dbrowser=chromium|firefox|webkit  -Dheadless=false  -Dgroups=smoke,login`  (config: `config/default.properties` + `config/<env>.properties` + git-ignored `config/<env>.local.properties`; prod needs `-Dprod.confirm=true`)

## Hard rules (apply everywhere)
- No `Thread.sleep`, no `System.out`, no hard-coded URLs/credentials (config + `AUTH_*` env vars).
- Pages/clients never assert; steps and tests assert with `PlaywrightAssertions` / AssertJ.
- `api/**` never imports `ui/**` and vice versa; shared code in `core/**`.
- Scenario state is injected (PicoContainer), never static.
- Every data-creating step registers cleanup.
- Never invent business rules — ask, in one batch.

## Current state (2026-10-19)
- Target: ThreatIDR v1.5.1 at http://localhost (Docker stack tidr-deploy-v1); config in the git-ignored
  `config/staging.local.properties`. Business rules: `docs/threatidr-source-facts.md` (from the app source).
- UI suite: 180 scenarios in the default run, all green on 2026-10-19 (about 20 min). 30 more are
  `@known-defect` (assert correct behaviour, fail on confirmed product bugs). `@wip`, `@blocked-env`,
  `@out-of-scope` and `@known-defect` are excluded from the default run. Tag policy and the shared step
  vocabulary: `src/test/resources/features/ui/README.md`. Coverage vs the plan:
  `../ThreatIDR_Automation_Coverage.xlsx`.
- Fixtures: persistent `qa-auto-*` limited users/roles (passwords in `.auth-state/<env>/fixture-users.properties`),
  disposable users per scenario. Locks: `@serial-users` -> `@serial-settings` -> login guard, all with timeouts.
- NEVER save RPZ category settings or a valid Access Control change against the shared bind9: the app
  rewrites `allow-query` and locks the test host out of DNS, and it cannot undo it.
- No REST API features (out of scope). Agent notes on environment traps: `.claude/agent-memory/bdd-step-writer/`.

### UI sessions and the login rate limit (2026-10-18)
- ThreatIDR rate-limits the login POST to 3/minute per IP, successful logins included. So the suite
  logs in **once per role**, saves the session to `.auth-state/<env>/<role>.json` (git-ignored) and
  reuses it in every later run: `StorageStateManager` probes the dashboard over HTTP with the saved
  cookies (about a second) and only logs in again when the file is missing, older than
  `auth.state.max.age.minutes`, or rejected. Scenarios reach it through `Given I am logged in as "admin"`.
- **ThreatIDR sessions expire 15 min after the request that last signed the cookie**
  (`app.permanent_session_lifetime = timedelta(minutes=15)` in `core/app.py`), and a saved file is
  frozen at save time. So every successful probe writes the server's freshly signed cookie back to
  the file, and a session in use is re-probed every `auth.state.refresh.minutes` (10) so a long run
  never outlives it. `auth.state.max.age.minutes` (14) skips the probe for a file that is certainly dead.
- `Given I am logged in as "admin"` also checks what actually rendered: if the login form appears
  (session ended by a logout elsewhere, a server restart or expiry), `StorageStateManager.ensureAuthenticated`
  logs in again in that scenario's context and re-saves the file, so logout scenarios and the rest of
  the suite coexist. The step is re-entrant: calling it again after `When I log out` re-establishes
  the session on the open page. Parallel scenarios hitting one dead session share a single re-login.
- `LoginRateLimitGuard` serialises real form submissions (`@serial-auth` scenarios and the one-time
  login) and models the server budget as a sliding window of 3 logins per 61 s: a `@serial-auth`
  scenario reserves 1 login, so three run back to back; `@serial-auth @login-window` (the two
  scenarios that deliberately trigger the 429) reserve the whole window. Nothing is spent at JVM
  start, so the first login of a run never waits. Logins from other processes are invisible to it:
  **never run two test JVMs (IDE + Maven) against the same app at once**, they will 429 each other.
  If an outside login leaves the window exhausted, the one-time login sees the "Rate Limit Exceeded"
  page, waits the window out once and retries.
- Tag `@serial-auth` on scenarios that submit the login form, never on whole features: every tagged
  scenario queues behind the guard. `login.feature` has a hard floor of ~4 min (13 login POSTs at
  3/min); its `@slow` scenario alone needs 61 s by design.

### Things that look like bugs in the glue and are deliberate
1. `Given the request has headers:` in a Background and again inside a scenario **merges with override**
   (the later table wins per header; `<none>` removes a header).
2. `{uniqueString}` / `{uuid}` / `{uniqueEmail}` are cached per scenario by `PlaceholderResolver`, so a
   write can assert the value it sent. `{now}` is not cached.
3. Response parsing tolerates an empty body (a 204 must not throw while parsing JSON).
