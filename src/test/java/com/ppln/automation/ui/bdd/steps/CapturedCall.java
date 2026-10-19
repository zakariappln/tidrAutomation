package com.ppln.automation.ui.bdd.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.ppln.automation.core.utils.JsonUtils;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.bdd.context.UiScenarioContext.HttpResult;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Captures the page's own AJAX call to one endpoint while a step drives the UI: the request passes
 * through {@link Page#route} untouched ({@link Route#fetch} + fulfil with the same response), and
 * its status and body are kept. Reading the body here, rather than from a later {@code
 * waitForResponse}, matters because ThreatIDR reloads the page right after most successful saves,
 * which makes the original response body unavailable. Each call is recorded in the scenario
 * context, so a 5xx also counts for "the request should not cause a server error".
 */
final class CapturedCall implements AutoCloseable {

    /**
     * The fetch would otherwise inherit the context's 10 s action timeout, which a summary PDF
     * (wkhtmltopdf, ~8 s idle, longer under load) exceeds: the request was then abandoned (nginx
     * 499) and sent again by {@code resume()}, and its answer never captured.
     */
    private static final double FETCH_TIMEOUT_MS = 120_000;

    private final UiScenarioContext ctx;
    private final Page page;
    private final String urlGlob;
    private final Consumer<Route> handler;
    private final AtomicReference<HttpResult> result = new AtomicReference<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicReference<String> refusal = new AtomicReference<>();

    private CapturedCall(
            UiScenarioContext ctx,
            String method,
            String urlGlob,
            Function<String, Optional<String>> guard) {
        this.ctx = ctx;
        this.page = ctx.page();
        this.urlGlob = urlGlob;
        this.handler =
                route -> {
                    if (!method.equals(route.request().method())) {
                        route.resume();
                        return;
                    }
                    started.set(true);
                    Optional<String> refused = guard.apply(route.request().postData());
                    if (refused.isPresent()) {
                        refusal.set(refused.get());
                        route.abort(); // never reaches the server
                        return;
                    }
                    APIResponse response;
                    try {
                        response =
                                route.fetch(new Route.FetchOptions().setTimeout(FETCH_TIMEOUT_MS));
                    } catch (RuntimeException e) {
                        route.resume(); // never leave the request pending
                        return;
                    }
                    HttpResult captured =
                            new HttpResult(
                                    method,
                                    route.request().url(),
                                    response.status(),
                                    response.text());
                    result.set(captured);
                    ctx.recordHttp(captured);
                    route.fulfill(new Route.FulfillOptions().setResponse(response));
                };
        // one match only: the route removes itself once used, so close() never has to unroute a
        // route whose handler may still be running (Page.unroute waits for it and can hang)
        page.route(urlGlob, handler, new Page.RouteOptions().setTimes(1));
    }

    /** Starts capturing {@code method} requests whose URL matches {@code urlGlob}. */
    static CapturedCall of(UiScenarioContext ctx, String method, String urlGlob) {
        return new CapturedCall(ctx, method, urlGlob, body -> Optional.empty());
    }

    /**
     * Like {@link #of}, but the request body is inspected first: when {@code guard} returns a
     * reason, the request is aborted before it reaches the server and every {@code await} fails
     * with that reason. Used where a request the scenario does not expect to succeed would, if it
     * did, change shared infrastructure (bind9's named.conf, the Celery workers).
     */
    static CapturedCall guarded(
            UiScenarioContext ctx,
            String method,
            String urlGlob,
            Function<String, Optional<String>> guard) {
        return new CapturedCall(ctx, method, urlGlob, guard);
    }

    /**
     * Waits until the call has completed or {@code blockedClientSide} reports that the page refused
     * to send it (client-side validation), whichever comes first.
     */
    Optional<HttpResult> await(BooleanSupplier blockedClientSide) {
        page.waitForCondition(
                () ->
                        result.get() != null
                                || refusal.get() != null
                                || blockedClientSide.getAsBoolean());
        failIfRefused();
        return Optional.ofNullable(result.get());
    }

    /** Waits for the call; fails if it is never sent. */
    HttpResult await() {
        page.waitForCondition(() -> result.get() != null || refusal.get() != null);
        failIfRefused();
        return result.get();
    }

    /** Waits up to {@code timeoutMs} for the call (slow endpoints such as PDF generation). */
    HttpResult await(double timeoutMs) {
        page.waitForCondition(
                () -> result.get() != null || refusal.get() != null,
                new Page.WaitForConditionOptions().setTimeout(timeoutMs));
        failIfRefused();
        return result.get();
    }

    private void failIfRefused() {
        if (refusal.get() != null) {
            throw new AssertionError("Request not sent (safety guard): " + refusal.get());
        }
    }

    /** The body as JSON, or a missing node when it is not JSON (e.g. an HTML error page). */
    static JsonNode json(HttpResult result) {
        try {
            return JsonUtils.readTree(result.body());
        } catch (RuntimeException e) {
            return JsonUtils.mapper().missingNode();
        }
    }

    @Override
    public void close() {
        // only a route that never fired is still registered (and has no running handler)
        if (!started.get() && ctx.hasPage()) {
            page.unroute(urlGlob, handler);
        }
    }
}
