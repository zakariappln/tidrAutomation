---
name: api-step-definition-writer
description: Implement Cucumber-TestNG step definitions (Java 25) that execute the API Gherkin features written with the fixed step vocabulary — request/response steps, preconditions, variable storage, placeholder resolution, expected-value matchers, JSON-schema checks, cleanup hooks, and the runner wired into testng-api.xml and Allure. Use whenever the user asks to implement, generate, fix or review step definitions, glue code, hooks, a Cucumber runner, "make the feature files run", an undefined/ambiguous step, or scenario state handling for API tests. Trigger even for a single new step phrase.
---

# API Step Definition Writer

You implement the glue between `.feature` files (written by `api-gherkin-test-case-writer`) and the API layer (`ApiClient`, resource clients from `playwright-java-project-architecture`). Steps are thin, scenario-scoped, parallel-safe, and fail with messages a reviewer can act on from the Allure report alone.

## The 10 rules (non-negotiable)

1. **One phrase → one method, verbatim from the vocabulary.** Implement phrases exactly as written in the Gherkin style guide. No near-duplicates. New phrase → add to vocabulary first, then implement once.
2. **Steps are glue.** Parse arguments → call framework → assert. No HTTP, JSON parsing or retry logic in step classes.
3. **Scenario state is injected, never static.** All state lives in a per-scenario `ScenarioContext` provided by PicoContainer. No `static` mutable fields, no `ThreadLocal`.
4. **Organise by responsibility.** `RequestSteps`, `ResponseSteps`, `PreconditionSteps`, `VariableSteps`, `Hooks`. Never one step class per feature.
5. **Placeholders resolve in one place.** `PlaceholderResolver` handles `{baseUrl}`, `{authToken}`, stored vars, `{uniqueEmail}`, `{now}`… Generated values are cached per scenario.
6. **Matchers are a closed, unit-tested set.** `ExpectedValueMatcher` implements every matcher in the style guide; body tables use JsonPath + AssertJ soft assertions.
7. **Failures are diagnosable.** Messages carry path/header, expected, actual, method + URL. Request/response are already in Allure via `ApiClient`.
8. **Preconditions register their own cleanup.** Creating steps push a deletion lambda; `@After` runs them LIFO, always.
9. **Contract steps are strict and cached.** Schemas load once per JVM; strict validation lists every violation; header names case-insensitive.
10. **Lint-clean and dry-run gated.** No `Thread.sleep`, `System.out`, swallowed exceptions or literals; compiles under `-Xlint:all -Werror`; `cucumber.execution.dry-run=true` runs in CI so undefined/ambiguous steps fail the build.

## Workflow

### A. First-time setup (no Cucumber in the project yet)

1. Read `references/runner-and-testng.md`. Add the Cucumber, PicoContainer and Allure-Cucumber dependencies to `pom.xml` (resolve latest stable versions; align `cucumber-*` on one version via `cucumber-bom`).
2. Create the package tree from `references/package-layout.md`.
3. Generate `ScenarioContext`, `PlaceholderResolver`, `ExpectedValueMatcher`, `SchemaRegistry` from `references/context-placeholders-matchers.md`.
4. Generate the five step classes from `references/step-classes.md` and `Hooks` from `references/hooks-and-cleanup.md`.
5. Generate `ApiCucumberRunner` and wire it into `testng-api.xml`; add `cucumber.properties` from `assets/`.
6. Run `mvn test -Dsuite=testng-api.xml -Dcucumber.execution.dry-run=true` — must report zero undefined and zero ambiguous steps. Then run for real with `-Dcucumber.filter.tags=@smoke`.
7. Confirm Allure shows scenarios with request/response attachments.

### B. Adding or fixing steps

1. `Grep` the step classes for the phrase. If it exists, do not add another pattern — fix or reuse it.
2. If new: confirm the phrase is in the style guide vocabulary (or propose the addition to the user). Place it in the class matching its responsibility.
3. Use Cucumber Expressions (`{string}`, `{int}`, `{word}`), not regex, unless a regex is unavoidable; then anchor it.
4. Resolve every string argument through `PlaceholderResolver` before use.
5. Run dry-run, then the affected feature. Report the diff and the run result.

### C. Reviewing existing glue

Walk the 10 rules as a checklist; report `BLOCKER / MAJOR / MINOR` with file:line; offer to fix. Typical blockers: static state, HTTP calls in steps, duplicate patterns, cleanup missing, `Thread.sleep`.

## Definition of done

- [ ] Dry-run: 0 undefined, 0 ambiguous, 0 pending steps.
- [ ] `mvn test -Dsuite=testng-api.xml` green in parallel (`parallel=true` data provider, ≥4 threads).
- [ ] Every creating step registers cleanup; `@After` leaves no data (verify by re-running the suite twice).
- [ ] Every assertion failure message shows path/header + expected + actual + `METHOD URL`.
- [ ] No `static` mutable field in any step/hook/context class.
- [ ] Unit tests exist for `PlaceholderResolver` and `ExpectedValueMatcher`.
- [ ] Allure report shows Feature → Scenario → steps with request/response attachments.

## Reference files

- `references/package-layout.md` — where glue code lives; dependency direction.
- `references/context-placeholders-matchers.md` — `ScenarioContext`, `PlaceholderResolver`, `ExpectedValueMatcher`, `SchemaRegistry` (full code).
- `references/step-classes.md` — `RequestSteps`, `ResponseSteps`, `PreconditionSteps`, `VariableSteps` (full code, one method per vocabulary phrase).
- `references/hooks-and-cleanup.md` — `Hooks`, cleanup stack, failure attachments, tag-driven behaviour.
- `references/runner-and-testng.md` — dependencies, `ApiCucumberRunner`, `testng-api.xml` wiring, tag filtering, Allure-Cucumber plugin, dry-run in CI.
- `assets/cucumber.properties` — copy to `src/test/resources/`.
