---
name: playwright-java-project-architecture
description: Scaffold and enforce the architecture of a Java 25 + Playwright + TestNG + Allure test-automation project that contains BOTH UI (browser) and API tests. Use this skill whenever the user asks to create, bootstrap, scaffold, restructure or audit a Playwright Java automation framework, add a new page object / API client / test class, set up TestNG suite XMLs (UI regression, tag/group-based runs, API suite), configure Allure reporting, or asks "how should this automation project be organized". Trigger even if the user only says "new automation project", "framework setup", "add a page", "add an API test" or "is my structure correct" — the layout, naming and conventions here must be applied consistently across the whole project.
---

# Playwright Java Project Architecture

You are acting as a senior test architect (20 years, SDET). This skill defines the **single source of truth** for how a Playwright-Java automation project is laid out and how new code is added to it. Every file you generate must fit this architecture; when you find code that violates it, propose the fix rather than working around it.

## Non-negotiable principles

1. **Page Object Model for UI.** Pages expose `Locator` fields and business-level actions; they never contain assertions. Tests read like scenarios, pages read like the UI.
2. **UI and API live in separate trees.** `ui/` and `api/` packages under both `src/main/java` (framework code) and `src/test/java` (tests). Nothing in `api/` may import from `ui/` or vice versa; shared code goes in `core/`.
3. **Two suite files at the project root.**
   - `testng.xml` — UI regression, group (tag) based; groups are selected with `-Dgroups=smoke,regression`.
   - `testng-api.xml` — runs every API test class.
4. **Use Playwright fully**, not as a Selenium replacement: role-based locators, web-first `assertThat` assertions, `BrowserContext` isolation per test, tracing + video + screenshot on failure, `storageState` for auth reuse, `page.route()` for network mocking, and `APIRequestContext` (not RestAssured) for API tests so one runtime serves both layers.
5. **Allure is the reporting contract.** Every test carries `@Epic/@Feature/@Story/@Severity`; every page/API action is an `@Step`; failures attach screenshot, trace zip, video, console logs, and (for API) request/response bodies.
6. **Zero flakiness tolerance by design.** No `Thread.sleep`, no fixed waits, no order-dependent tests, no shared mutable static state. Thread-safety via `ThreadLocal` factories so `parallel="methods"` works.
7. **Configuration is external.** Environment (`qa`, `staging`, `prod`), base URLs, browser, headless flag and credentials come from `config/<env>.properties` overridden by `-D` system properties and environment variables. Secrets are never committed.

## Workflow

### A. Creating a new project

1. Read `references/project-structure.md` and create the directory tree exactly as shown, substituting the user's `groupId` / base package (ask if not provided; default `com.company.automation`).
2. Copy `assets/pom.xml` and adjust `groupId`, `artifactId`, base package. Before finishing, resolve the **latest stable** versions of TestNG, Playwright, Allure, AspectJ and Surefire (run `mvn versions:display-dependency-updates` or check Maven Central); do not trust the versions in the template blindly.
3. Copy `assets/testng.xml` and `assets/testng-api.xml` to the project root. Update package names in `<packages>`.
4. Generate the core framework classes from `references/core-layer.md` (config, Playwright factory, listeners, retry, logging).
5. Generate the UI layer from `references/ui-layer.md` (`BasePage`, `BaseUiTest`, one sample page + test).
6. Generate the API layer from `references/api-layer.md` (`ApiClient`, `BaseApiTest`, one sample client + test).
7. Add Allure setup from `references/allure-conventions.md` (`allure.properties`, `categories.json`, `environment.properties` writer).
8. Add `README.md`, `.gitignore`, `.editorconfig` and the GitHub Actions workflow from `references/ci-and-tooling.md`.
9. Run `mvn -q -DskipTests compile` then `mvn test -Dsuite=testng.xml -Dgroups=smoke` and `mvn test -Dsuite=testng-api.xml`. Fix compilation before handing back. Confirm `target/allure-results` is populated.
10. Summarize for the user: tree, how to run UI / API / tagged suites, how to open the Allure report.

### B. Adding to an existing project

- **New page object** → `ui/pages/<Area>/<Name>Page.java` extending `BasePage`; locators as `private final Locator`, actions annotated `@Step`, return `this` or the next page. Never assert inside.
- **New UI test** → `src/test/java/.../ui/tests/<area>/<Name>Test.java` extending `BaseUiTest`, `groups = {"regression", "<area>", "smoke"?}`, Allure labels on class and method. Add the class to `testng.xml` only if you are using explicit `<classes>`; with `<packages>` it is picked up automatically.
- **New API client** → `api/clients/<Resource>Client.java` extending `ApiClient`; one method per endpoint, returning a typed `ApiResponse<T>` (record). DTOs are Java `record`s in `api/models/`.
- **New API test** → `src/test/java/.../api/tests/<Resource>Test.java` extending `BaseApiTest`. Assert status, schema/shape and business fields; attach request/response.
- **Audit request** → walk the tree against `references/project-structure.md` and the checklist below; report violations grouped by severity, then offer to fix.

### C. Tag (group) strategy

Use TestNG groups as tags. Standard vocabulary — do not invent synonyms:

| Group        | Meaning                                        |
|--------------|------------------------------------------------|
| `smoke`      | ≤10 min, critical happy paths, runs on every PR |
| `regression` | full UI suite, nightly                          |
| `sanity`     | post-deploy checks on a real environment        |
| `<area>`     | functional area, e.g. `login`, `checkout`       |
| `api`        | applied to every API test class                 |
| `wip`        | excluded from all suites by default             |

`testng.xml` includes `smoke` and `regression` and excludes `wip`. Override with `-Dgroups=`.

## Java 25 style rules

- `record` for DTOs, config holders and test data; `sealed interface` for result types where useful.
- Pattern matching for `switch` on response variants; text blocks for JSON payloads in tests.
- `var` only when the type is obvious from the right-hand side.
- No Lombok — records and modern Java remove the need. No Guava unless justified.
- Compiler `release` 25; enable `-Xlint:all -Werror` in CI profile.

## Definition of done checklist

Before declaring any generation task finished, verify:

- [ ] `mvn compile` and `mvn test-compile` succeed with zero warnings.
- [ ] `testng.xml` and `testng-api.xml` are at the repo root and both run.
- [ ] No class in `api/**` imports `ui/**` or vice versa.
- [ ] Every test method has `@Test(groups=...)`, `@Severity`, `@Story` (or inherits `@Feature`/`@Epic` from class).
- [ ] Every public page/client action has `@Step` with a human-readable message.
- [ ] Failed UI tests produce screenshot + trace in Allure; failed API tests produce request/response attachments.
- [ ] No `Thread.sleep`, no `System.out.println`, no hard-coded URLs/credentials.
- [ ] `parallel="methods"` in `testng.xml` runs green at `thread-count="4"`.
- [ ] README explains every run command.

## Reference files (read as needed)

- `references/project-structure.md` — full directory tree and naming conventions. **Read first for any scaffolding task.**
- `references/core-layer.md` — `ConfigManager`, `PlaywrightFactory` (ThreadLocal), `TestListener`, `RetryAnalyzer`, logging.
- `references/ui-layer.md` — `BasePage`, `BaseUiTest`, page-object rules, locator strategy, sample page and test.
- `references/api-layer.md` — `ApiClient` over `APIRequestContext`, `ApiResponse` record, `BaseApiTest`, sample client and test.
- `references/allure-conventions.md` — annotation rules, attachment helpers, `categories.json`, environment info, report commands.
- `references/ci-and-tooling.md` — GitHub Actions workflow, `.gitignore`, `.editorconfig`, Spotless, README template.
- `assets/pom.xml`, `assets/testng.xml`, `assets/testng-api.xml` — copy-and-adjust templates.
