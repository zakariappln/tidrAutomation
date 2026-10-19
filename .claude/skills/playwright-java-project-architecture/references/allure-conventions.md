# Allure Conventions

## Dependencies

- `io.qameta.allure:allure-testng` — lifecycle integration.
- `io.qameta.allure:allure-playwright` — turns Playwright actions into Allure steps automatically (optional but recommended; verify it exists for the chosen Playwright version).
- `io.qameta.allure:allure-java-commons` — attachments API.
- AspectJ weaver as `-javaagent` in Surefire `argLine` — required for `@Step` and `@Attachment` to work.
- `allure-bom` in `<dependencyManagement>` keeps all Allure artifacts on one version.

## Annotation contract

| Level  | Required                                            |
|--------|-----------------------------------------------------|
| Class  | `@Epic("<product area>")`, `@Feature("<feature>")`  |
| Method | `@Story("<user story>")`, `@Severity(...)`, `@Test(description=...)`; `@TmsLink`/`@Issue` when a tracker exists |
| Action | `@Step("<human sentence with {param}>")` on every public page and client method |

Severity mapping: `BLOCKER` = product unusable; `CRITICAL` = core flow broken; `NORMAL` = default; `MINOR` = cosmetic; `TRIVIAL` = rarely used.

Use Allure labels, not test-name prefixes, to carry metadata. TestNG groups remain the execution filter; Allure `@Tag` may mirror them via `SuiteListener` if the user wants tag filtering inside the report.

## AllureAttachments helper (core/reporting)

```java
package com.company.automation.core.reporting;

import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AllureAttachments {

    private static final ThreadLocal<List<String>> CONSOLE = ThreadLocal.withInitial(CopyOnWriteArrayList::new);

    private AllureAttachments() {}

    public static void screenshot(Page page, String name) {
        byte[] png = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
        Allure.addAttachment(name, "image/png", new ByteArrayInputStream(png), ".png");
    }

    public static void trace(Path zip) { file("Playwright trace", zip, "application/zip", ".zip"); }
    public static void video(Path webm) { file("Video", webm, "video/webm", ".webm"); }

    public static void text(String name, String content) {
        Allure.addAttachment(name, "text/plain", mask(content), ".txt");
    }

    public static void json(String name, String content) {
        Allure.addAttachment(name, "application/json", mask(content), ".json");
    }

    public static void collectConsole(ConsoleMessage msg) {
        CONSOLE.get().add("[%s] %s".formatted(msg.type(), msg.text()));
    }

    public static void consoleLogs() {
        var logs = CONSOLE.get();
        if (!logs.isEmpty()) text("Browser console", String.join("\n", logs));
        logs.clear();
    }

    private static void file(String name, Path path, String mime, String ext) {
        try {
            Allure.addAttachment(name, mime, Files.newInputStream(path), ext);
        } catch (IOException e) {
            text(name + " (unavailable)", e.toString());
        }
    }

    /** Never leak secrets into reports. */
    private static String mask(String s) {
        return s == null ? "" : s.replaceAll("(?i)(authorization\\W+)(bearer\\s+)?\\S+", "$1***")
                                  .replaceAll("(?i)(\"password\"\\s*:\\s*\")[^\"]*", "$1***");
    }
}
```

Trace view: the attached `.zip` opens at https://trace.playwright.dev — mention this in the README.

## Resources

`src/test/resources/allure.properties`
```
allure.results.directory=target/allure-results
allure.link.tms.pattern=https://tracker.example.com/browse/{}
allure.link.issue.pattern=https://tracker.example.com/browse/{}
```

`src/test/resources/categories.json` (copied to results by `SuiteListener`)
```json
[
  { "name": "Product defects",  "matchedStatuses": ["failed"],  "messageRegex": ".*(AssertionError|expected).*" },
  { "name": "Test infrastructure", "matchedStatuses": ["broken"], "messageRegex": ".*(TimeoutError|PlaywrightException|net::ERR).*" },
  { "name": "Flaky (passed on retry)", "matchedStatuses": ["passed"], "flaky": true },
  { "name": "Skipped / WIP", "matchedStatuses": ["skipped"] }
]
```

`SuiteListener#onStart` writes `target/allure-results/environment.properties`:
```
Environment=qa
Browser=chromium (headless)
UI.BaseUrl=https://qa.example.com
API.BaseUrl=https://api.qa.example.com
Java=25
Playwright=<version from Playwright class package>
Executor=<CI name or local user>
```
and copies `categories.json` next to it. Also write `executor.json` in CI (build name/url) for the trend widget.

## Commands (put in README)

```bash
mvn clean test -Dsuite=testng.xml -Dgroups=smoke          # UI smoke
mvn clean test -Dsuite=testng.xml                          # UI full regression
mvn clean test -Dsuite=testng-api.xml                      # API suite
mvn clean test -Dsuite=testng.xml -Dbrowser=firefox -Dheadless=false -Denv=staging
mvn allure:serve                                           # build + open report
mvn allure:report                                          # static report in target/site/allure-maven-plugin
```

History/trend: in CI copy `allure-report/history` into `allure-results/history` before generating; see `ci-and-tooling.md`.
