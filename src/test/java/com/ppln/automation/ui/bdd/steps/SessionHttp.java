package com.ppln.automation.ui.bdd.steps;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.RequestOptions;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;

/**
 * Requests sent over the scenario's own browser session (same cookies) with the open page's CSRF
 * token, for the cases where the UI hides a control and the server-side guard is what is under
 * test. Each result is recorded in the context ({@link UiScenarioContext#lastHttp()}).
 */
final class SessionHttp {

    private SessionHttp() {}

    /** The token the app's own JS sends as {@code X-CSRFToken} (meta tag on every page). */
    static String csrfToken(Page page) {
        String token = page.locator("meta#csrf-token").getAttribute("content");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("No csrf-token meta on " + page.url());
        }
        return token;
    }

    static RequestOptions withCsrf(Page page) {
        return RequestOptions.create().setHeader("X-CSRFToken", csrfToken(page));
    }

    static HttpResult send(
            UiScenarioContext ctx, String method, String path, RequestOptions options) {
        Page page = ctx.page();
        RequestOptions o = options == null ? withCsrf(page) : options;
        APIResponse response =
                switch (method) {
                    case "GET" -> page.request().get(path, o);
                    case "PUT" -> page.request().put(path, o);
                    case "POST" -> page.request().post(path, o);
                    case "DELETE" -> page.request().delete(path, o);
                    default -> throw new IllegalArgumentException("Unsupported method " + method);
                };
        HttpResult result = new HttpResult(method, path, response.status(), response.text());
        ctx.recordHttp(result);
        return result;
    }
}
