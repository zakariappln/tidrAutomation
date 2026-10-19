package com.ppln.automation.api.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

@Epic("Framework")
@Feature("cURL attachment")
public class CurlCommandTest {

    private static final String BASE = "https://api.example.test/";
    private static final Map<String, String> TENANT =
            Map.of("X-Tenant-Id", "11111111-2222-3333-4444-555555555555");

    @Test(
            groups = {"unit"},
            description = "JSON body: full URL, context + request headers, token from $USBA_TOKEN")
    public void rendersJsonPost() {
        RequestSpec spec =
                RequestSpec.empty()
                        .withHeader("Authorization", "Bearer eyJ.real.token")
                        .withHeader("X-Request-Id", "req-1")
                        .withRawBody("{\"itemName\":\"O'Brien Ltd\"}");
        String curl =
                CurlCommand.render("post", BASE, "/api/Item/Add", TENANT, spec, spec.rawBody());

        assertThat(curl).doesNotContain("eyJ.real.token");
        assertThat(CurlCommand.lines(curl.substring(curl.indexOf("curl "))))
                .contains(
                        "curl -i -X POST 'https://api.example.test/api/Item/Add'",
                        "-H 'X-Tenant-Id: 11111111-2222-3333-4444-555555555555'",
                        "-H \"Authorization: Bearer ${USBA_TOKEN}\"",
                        "-H 'X-Request-Id: req-1'",
                        "-H 'Content-Type: application/json'",
                        "--data-raw '{\"itemName\":\"O'\\''Brien Ltd\"}'");
        assertThat(curl).startsWith("# export USBA_TOKEN=");
    }

    @Test(
            groups = {"unit"},
            description = "A request header overrides the context default of the same name")
    public void requestHeaderOverridesDefault() {
        RequestSpec spec = RequestSpec.empty().withHeader("x-tenant-id", "1");
        String curl = CurlCommand.render("GET", BASE, "/api/Item/GetAll", TENANT, spec, null);
        assertThat(curl)
                .contains("-H 'x-tenant-id: 1'")
                .doesNotContain("fc079700")
                .doesNotContain("--data-raw");
    }

    @Test(
            groups = {"unit"},
            description = "Query parameters are URL-encoded")
    public void encodesQuery() {
        RequestSpec spec = RequestSpec.empty().withQuery("Search", "a b&c");
        assertThat(CurlCommand.url(BASE, "/api/Item/GetAll", spec.query()))
                .isEqualTo("https://api.example.test/api/Item/GetAll?Search=a%20b%26c");
    }

    @Test(
            groups = {"unit"},
            description = "Multipart: -F parts, file references, no explicit Content-Type")
    public void rendersMultipart() {
        RequestSpec spec =
                RequestSpec.empty()
                        .withHeader("Content-Type", "application/json")
                        .withMultipart(
                                List.of(
                                        MultipartField.text("TransactionNo", "JV-1"),
                                        MultipartField.file(
                                                "Files",
                                                "a.pdf",
                                                "application/pdf",
                                                new byte[] {1})));
        String curl =
                CurlCommand.render("POST", BASE, "/api/Document/Upload", Map.of(), spec, null);
        assertThat(curl)
                .contains(
                        "-F 'TransactionNo=JV-1'",
                        "-F 'Files=@a.pdf;type=application/pdf'",
                        "testdata/files")
                .doesNotContain("Content-Type");
    }
}
