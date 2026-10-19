package com.ppln.automation.api.base;

import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Playwright;
import com.ppln.automation.api.auth.AuthHeaders;
import com.ppln.automation.api.client.ApiClient;
import com.ppln.automation.core.config.ConfigManager;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

/**
 * Base for plain TestNG API tests: one authenticated {@link APIRequestContext} per class per thread
 * ({@code parallel="classes"} in testng-api.xml). Override {@link #role()} for a different
 * principal.
 */
public abstract class BaseApiTest {

    private Playwright playwright;
    protected APIRequestContext request;

    @BeforeClass(alwaysRun = true)
    public void createRequestContext() {
        playwright = Playwright.create();
        request = ApiClient.newContext(playwright, AuthHeaders.authenticated(role()));
    }

    @AfterClass(alwaysRun = true)
    public void dispose() {
        if (request != null) {
            request.dispose();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    /**
     * Credentials role used for the class-level context; {@code default} maps to
     * AUTH_USERNAME/AUTH_PASSWORD.
     */
    protected String role() {
        return ConfigManager.DEFAULT_ROLE;
    }

    /** Builds an extra context (e.g. anonymous or another role) that is disposed with the class. */
    protected APIRequestContext contextFor(java.util.Map<String, String> headers) {
        return ApiClient.newContext(playwright, headers);
    }
}
