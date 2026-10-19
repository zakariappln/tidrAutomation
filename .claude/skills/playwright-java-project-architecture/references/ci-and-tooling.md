# CI and Tooling

## .gitignore

```
target/
*.class
*.log
.idea/
*.iml
.vscode/
.DS_Store
/allure-results/
/allure-report/
target/auth/            # storageState files contain session cookies
.env
*.local.properties
```

## .editorconfig

```
root = true
[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
indent_style = space
indent_size = 4
trim_trailing_whitespace = true
[*.{xml,json,yml,yaml}]
indent_size = 2
```

## Formatting / static checks (pom plugins, `ci` profile)

- `com.diffplug.spotless:spotless-maven-plugin` with `googleJavaFormat` (AOSP style, 4 spaces) — `mvn spotless:apply` locally, `spotless:check` in CI.
- `maven-compiler-plugin` `<compilerArgs><arg>-Xlint:all</arg><arg>-Werror</arg></compilerArgs>` in the `ci` profile only, so local iteration stays fast.
- Optional: `com.github.spotbugs:spotbugs-maven-plugin` for null-safety.

## GitHub Actions `.github/workflows/ci.yml`

```yaml
name: automation

on:
  pull_request:
  schedule:
    - cron: "0 2 * * *"        # nightly regression 02:00 UTC
  workflow_dispatch:
    inputs:
      groups:
        description: TestNG groups (comma separated)
        default: regression
      env:
        description: Target environment
        default: qa

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  api:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '25', cache: maven }
      - run: mvn -B clean test -Dsuite=testng-api.xml -Denv=${{ inputs.env || 'qa' }}
        env:
          AUTH_USERNAME: ${{ secrets.AUTH_USERNAME }}
          AUTH_PASSWORD: ${{ secrets.AUTH_PASSWORD }}
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: allure-results-api, path: target/allure-results }

  ui:
    runs-on: ubuntu-latest
    container: mcr.microsoft.com/playwright/java:v<PLAYWRIGHT_VERSION>-noble   # browsers pre-installed; pin to pom version
    strategy:
      fail-fast: false
      matrix:
        browser: [chromium, firefox, webkit]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '25', cache: maven }
      - name: Run UI suite
        run: >
          mvn -B clean test -Dsuite=testng.xml
          -Dgroups=${{ github.event_name == 'pull_request' && 'smoke' || (inputs.groups || 'regression') }}
          -Dbrowser=${{ matrix.browser }} -Denv=${{ inputs.env || 'qa' }}
        env:
          AUTH_USERNAME: ${{ secrets.AUTH_USERNAME }}
          AUTH_PASSWORD: ${{ secrets.AUTH_PASSWORD }}
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: allure-results-ui-${{ matrix.browser }}
          path: |
            target/allure-results
            target/traces
            target/videos

  report:
    needs: [api, ui]
    if: always()
    runs-on: ubuntu-latest
    steps:
      - uses: actions/download-artifact@v4
        with: { pattern: allure-results-*, merge-multiple: true, path: allure-results }
      - uses: actions/checkout@v4
        with: { ref: gh-pages, path: gh-pages }
        continue-on-error: true
      - name: Restore history
        run: mkdir -p allure-results/history && cp -r gh-pages/history/* allure-results/history/ 2>/dev/null || true
      - uses: simple-elf/allure-report-action@v1
        with: { allure_results: allure-results, allure_report: allure-report }
      - uses: peaceiris/actions-gh-pages@v4
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: allure-report
```

Adjust the runner container tag to the Playwright version in `pom.xml`; a mismatch causes "Executable doesn't exist" at browser launch. Locally, browsers are installed with `mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps"`.

## README template

```
# <Project> Automation

Java 25 · Playwright · TestNG · Allure — UI + API automation.

## Prerequisites
- JDK 25, Maven 3.9+
- `mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps"`
- Export `AUTH_USERNAME`, `AUTH_PASSWORD`

## Running
| Goal | Command |
|------|---------|
| UI smoke | `mvn clean test -Dsuite=testng.xml -Dgroups=smoke` |
| UI regression | `mvn clean test -Dsuite=testng.xml` |
| UI by area | `mvn clean test -Dsuite=testng.xml -Dgroups=checkout` |
| API | `mvn clean test -Dsuite=testng-api.xml` |
| Other browser / env | `-Dbrowser=firefox -Denv=staging -Dheadless=false` |
| Report | `mvn allure:serve` |

## Structure
(paste tree from project-structure.md)

## Conventions
- Page Object Model, no assertions in pages, `@Step` on every action
- Groups: smoke / regression / sanity / <area> / api / wip
- Traces: open `target/traces/*.zip` at https://trace.playwright.dev
```
