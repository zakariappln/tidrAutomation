package com.ppln.automation.api.client;

import com.microsoft.playwright.APIRequestContext;

/** Concrete client for endpoint-agnostic calls (Cucumber glue, ad-hoc probes). */
public final class RawApiClient extends ApiClient {

    public RawApiClient(APIRequestContext request) {
        super(request);
    }
}
