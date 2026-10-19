# UI Layer

## Page Object rules

1. A page object owns exactly one screen (or one reusable component). Constructor takes `Page`.
2. Locators are `private final Locator` fields initialised in the constructor. Prefer, in order: `getByRole` → `getByLabel` → `getByPlaceholder` → `getByText` → `getByTestId` → CSS. XPath only with a code comment justifying it.
3. Public methods are business actions (`login(user)`, `addToCart(sku)`), annotated `@Step("...")`, and return either `this` (stay on page) or the next page object (navigation). Never `void` unless there is truly nothing sensible to return.
4. **No assertions in pages.** Expose state via `Locator` getters or boolean/`String` queries (`errorMessage()`, `isLoggedIn()`); the test asserts with `PlaywrightAssertions.assertThat`.
5. No waits in pages. Playwright auto-waits; when you need a condition, use `assertThat(locator).isVisible()` in the **test**, or `locator.waitFor()` in the page only for non-actionable elements (e.g. spinner disappearance) — never `Thread.sleep`.
6. No test data or config lookups inside pages. Pages receive data as parameters.

## BasePage

```java
package com.company.automation.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) { this.page = page; }

    /** Relative to BrowserContext baseURL. */
    protected abstract String path();

    @Step("Open page {this.path}")
    public <T extends BasePage> T open() {
        page.navigate(path());
        @SuppressWarnings("unchecked") T self = (T) this;
        return self;
    }

    public Page page() { return page; }

    protected Locator byTestId(String id) { return page.getByTestId(id); }
}
```

## Sample page

```java
package com.company.automation.ui.pages.auth;

import com.company.automation.ui.pages.BasePage;
import com.company.automation.ui.pages.dashboard.DashboardPage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;

public final class LoginPage extends BasePage {

    private final Locator emailInput;
    private final Locator passwordInput;
    private final Locator signInButton;
    private final Locator errorBanner;

    public LoginPage(Page page) {
        super(page);
        emailInput    = page.getByLabel("Email");
        passwordInput = page.getByLabel("Password");
        signInButton  = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in"));
        errorBanner   = page.getByRole(AriaRole.ALERT);
    }

    @Override protected String path() { return "/login"; }

    @Step("Log in as {email}")
    public DashboardPage loginAs(String email, String password) {
        fillCredentials(email, password);
        signInButton.click();
        return new DashboardPage(page);
    }

    @Step("Attempt invalid login for {email}")
    public LoginPage loginExpectingError(String email, String password) {
        fillCredentials(email, password);
        signInButton.click();
        return this;
    }

    private void fillCredentials(String email, String password) {
        emailInput.fill(email);
        passwordInput.fill(password);
    }

    public Locator errorBanner() { return errorBanner; }
}
```

## BaseUiTest

```java
package com.company.automation.ui.base;

import com.company.automation.core.config.ConfigManager;
import com.company.automation.core.listeners.TestListener;
import com.company.automation.core.reporting.AllureAttachments;
import com.company.automation.ui.driver.PlaywrightFactory;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import org.testng.ITestResult;
import org.testng.annotations.*;
import java.lang.reflect.Method;
import java.nio.file.Path;

@Listeners(TestListener.class)
public abstract class BaseUiTest {

    protected Page page;

    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        PlaywrightAssertions.setDefaultAssertionTimeout(ConfigManager.config().expectTimeoutMs());
        page = PlaywrightFactory.startContext(storageState());
        page.onConsoleMessage(AllureAttachments::collectConsole);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        boolean failed = result.getStatus() == ITestResult.FAILURE;
        String name = result.getMethod().getMethodName();
        Path video = failed && page != null && page.video() != null ? page.video().path() : null;
        Path trace = PlaywrightFactory.stopContext(name, failed);
        if (trace != null) AllureAttachments.trace(trace);
        if (video != null) AllureAttachments.video(video);
    }

    @AfterClass(alwaysRun = true)
    public void closeBrowser() { PlaywrightFactory.shutdown(); }

    /** Override in subclasses to run authenticated: return StorageStateManager.forRole(Role.ADMIN). */
    protected Path storageState() { return null; }
}
```

Note: `page.video().path()` must be read before the context closes; the file is only complete after `stopContext`. That is why the order above matters.

## Sample UI test

```java
package com.company.automation.ui.tests.auth;

import com.company.automation.ui.base.BaseUiTest;
import com.company.automation.ui.pages.auth.LoginPage;
import io.qameta.allure.*;
import org.testng.annotations.Test;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Epic("Authentication")
@Feature("Login")
public class LoginTest extends BaseUiTest {

    @Test(groups = {"smoke", "regression", "login"}, description = "Valid user lands on dashboard")
    @Story("Successful login")
    @Severity(SeverityLevel.BLOCKER)
    @TmsLink("TC-101")
    public void shouldOpenDashboardWhenCredentialsAreValid() {
        var dashboard = new LoginPage(page).open().loginAs("qa.user@example.com", System.getenv("AUTH_PASSWORD"));
        assertThat(dashboard.welcomeHeading()).hasText("Welcome back");
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/dashboard"));
    }

    @Test(groups = {"regression", "login"})
    @Story("Failed login")
    @Severity(SeverityLevel.CRITICAL)
    public void shouldShowErrorWhenPasswordIsWrong() {
        var login = new LoginPage(page).open().loginExpectingError("qa.user@example.com", "wrong");
        assertThat(login.errorBanner()).containsText("Invalid credentials");
    }
}
```

## Playwright features to use deliberately

| Need                          | Use                                                           |
|-------------------------------|---------------------------------------------------------------|
| Skip login in every test      | `StorageStateManager` — log in once per role in `@BeforeSuite`, save `storageState.json` to `target/auth/<role>.json`, reuse via `setStorageStatePath` |
| Stub a backend / force errors | `page.route("**/api/orders", r -> r.fulfill(new Route.FulfillOptions().setStatus(500)))` |
| Wait for a call               | `page.waitForResponse("**/api/cart", () -> addButton.click())` |
| Data setup                    | `page.request()` or the API layer via `BaseUiTest#api()` — never through the UI |
| Visual check                  | `assertThat(page).hasScreenshot()` only in a dedicated `visual` group |
| Multi-tab                     | `context.waitForPage(() -> link.click())`                     |
| Downloads                     | `page.waitForDownload(() -> exportButton.click())`            |
| Mobile emulation              | `ContextOptionsBuilder` with `setViewportSize` + `setIsMobile(true)` + `setDeviceScaleFactor(2)` |
| Debugging                     | `-Dheadless=false -Dslow.mo.ms=300` and `PWDEBUG=1`           |

## Components

Reusable widgets extend `BaseComponent(Page page, Locator root)` and scope every locator with `root.locator(...)` / `root.getByRole(...)`. Pages expose components as fields: `public HeaderComponent header()`.

## Data providers

Test data lives in `src/test/resources/testdata/ui/*.json`, loaded into records by `TestDataLoader`, exposed through `@DataProvider(parallel = true)`. Do not hard-code multiple users inline.
