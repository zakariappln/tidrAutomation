# Runner, Dependencies and TestNG Wiring

## Dependencies (add to pom.xml; resolve latest stable, keep all `io.cucumber` on one version)

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.cucumber</groupId>
      <artifactId>cucumber-bom</artifactId>
      <version>${cucumber.version}</version>   <!-- baseline 7.2x.x; verify -->
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency><groupId>io.cucumber</groupId><artifactId>cucumber-java</artifactId><scope>test</scope></dependency>
  <dependency><groupId>io.cucumber</groupId><artifactId>cucumber-testng</artifactId><scope>test</scope></dependency>
  <dependency><groupId>io.cucumber</groupId><artifactId>cucumber-picocontainer</artifactId><scope>test</scope></dependency>
  <dependency><groupId>io.qameta.allure</groupId><artifactId>allure-cucumber7-jvm</artifactId><scope>test</scope></dependency>
  <dependency><groupId>com.jayway.jsonpath</groupId><artifactId>json-path</artifactId><version>${jsonpath.version}</version><scope>test</scope></dependency>
</dependencies>
```

`json-schema-validator` (networknt), AssertJ and Jackson already come from the architecture skill's pom.

## cucumber.properties (`src/test/resources/`)

See `assets/cucumber.properties`. Key entries: glue package, plugin list (Allure + pretty + JSON for CI), `snippet-type=camelcase`, `publish-quiet=true`, `filter.tags` default `not @wip`.

## ApiCucumberRunner

```java
package com.company.automation.api.bdd;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test(groups = "api")                      // makes the whole runner part of the `api` TestNG group
@CucumberOptions(
    features = "src/test/resources/features/api",
    glue = "com.company.automation.api.bdd",
    plugin = {
        "pretty",
        "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",
        "json:target/cucumber/api.json"
    },
    snippets = CucumberOptions.SnippetType.CAMELCASE
)
public class ApiCucumberRunner extends AbstractTestNGCucumberTests {

    /** Scenario-level parallelism; safe because all state is per-scenario (PicoContainer). */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
```

Tag filtering at run time: `-Dcucumber.filter.tags="@smoke and not @wip"`. System properties override `cucumber.properties`.

## testng-api.xml wiring

Add a second `<test>` block so plain TestNG API tests and BDD scenarios run in the same suite, same listeners, same Allure results:

```xml
  <test name="API Tests (TestNG)" preserve-order="false">
    <groups><run><include name="api"/><exclude name="wip"/></run></groups>
    <packages><package name="com.company.automation.api.tests.*"/></packages>
  </test>

  <test name="API Scenarios (Gherkin)" preserve-order="false">
    <groups><run><include name="api"/></run></groups>
    <classes><class name="com.company.automation.api.bdd.ApiCucumberRunner"/></classes>
  </test>
```

Thread count for the data provider comes from the suite's `data-provider-thread-count="8"` attribute — set it on `<suite>`.

## Mapping TestNG `-Dgroups` to Cucumber tags

The framework's convention is `-Dgroups=smoke`. Keep one switch for users by translating in the runner:

```java
static {
    var groups = System.getProperty("groups", "");
    if (!groups.isBlank() && System.getProperty("cucumber.filter.tags") == null) {
        var expr = java.util.Arrays.stream(groups.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).map(g -> "@" + g)
            .collect(java.util.stream.Collectors.joining(" or "));
        System.setProperty("cucumber.filter.tags", "(" + expr + ") and not @wip");
    }
}
```

`-Dgroups=smoke,users` → TestNG runs groups `smoke|users`, Cucumber runs `(@smoke or @users) and not @wip`.

## Dry-run gate (CI, before the real run)

```bash
mvn -q test -Dsuite=testng-api.xml -Dcucumber.execution.dry-run=true -Dcucumber.filter.tags=""
```

Dry-run exits non-zero on undefined or ambiguous steps and prints snippets for missing ones. Put it as its own CI step so an unimplemented phrase fails fast, before browsers or environments are touched.

## Allure specifics

- `AllureCucumber7Jvm` maps Feature → Allure Feature, Scenario → test, Gherkin steps → Allure steps, `Examples` rows → parameters. Request/response attachments from `RequestSteps` appear under the `When` step.
- Feature-level tags become Allure tags; `@TC-123` / `@JIRA-456` become TMS/issue links via the hook shown in `hooks-and-cleanup.md` (patterns from `allure.properties`).
- Do not also register `AllureTestNg` for the runner class twice — the suite-level listener from `testng-api.xml` is enough; the Cucumber plugin handles scenario granularity.

## Common failures and the fix

| Symptom                                            | Cause / fix                                                             |
|----------------------------------------------------|-------------------------------------------------------------------------|
| `Undefined step` in dry-run                        | phrase not in vocabulary or typo; implement or fix feature, never regex-widen |
| `AmbiguousStepDefinitionsException`                | two patterns match; delete the newer duplicate                          |
| Scenarios interfere under parallel                 | static field somewhere in glue; move into `ScenarioContext`             |
| `No stored variable {userId}`                      | `store` step missing or precondition failed silently; check assertion in precondition |
| Cleanup 401                                        | precondition used scenario auth instead of `adminContext()`             |
| Allure shows steps but no attachments              | `RequestSteps.send` bypassed; all sends must go through it              |
