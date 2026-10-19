package com.ppln.automation.api.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.ppln.automation.api.bdd.context.ExpectedValueMatcher;
import com.ppln.automation.api.bdd.context.ScenarioContext;
import com.ppln.automation.api.bdd.context.SchemaRegistry;
import com.ppln.automation.core.bdd.DataTables;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import java.util.Locale;
import org.assertj.core.api.SoftAssertions;

/** Then: status, headers, schema, body. Every message carries {@code METHOD PATH} for triage. */
public class ResponseSteps {

    private final ScenarioContext ctx;

    public ResponseSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int expected) {
        var response = ctx.lastResponse();
        assertThat(response.status())
                .as("%s -> status. Body: %s", ctx.lastCall(), abbreviate(response.rawBody()))
                .isEqualTo(expected);
    }

    @Then("the response headers contain:")
    public void theResponseHeadersContain(DataTable table) {
        var response = ctx.lastResponse();
        var soft = new SoftAssertions();
        DataTables.asMap(table)
                .forEach(
                        (name, expected) -> {
                            String actual = response.headers().get(name.toLowerCase(Locale.ROOT));
                            String reason =
                                    ExpectedValueMatcher.check(
                                            actual == null ? ExpectedValueMatcher.ABSENT : actual,
                                            ctx.resolve(expected));
                            soft.assertThat(reason)
                                    .as("%s -> header '%s'", ctx.lastCall(), name)
                                    .isNull();
                        });
        soft.assertAll();
    }

    @Then("the response body matches schema {string}")
    public void theResponseBodyMatchesSchema(String schemaPath) {
        String violations = SchemaRegistry.validate(schemaPath, ctx.lastResponse().rawBody());
        assertThat(violations)
                .as("%s -> body does not match %s:%n%s", ctx.lastCall(), schemaPath, violations)
                .isEmpty();
    }

    @Then("the response body has:")
    public void theResponseBodyHas(DataTable table) {
        DocumentContext doc = JsonPath.parse(ctx.lastResponse().rawBody());
        var soft = new SoftAssertions();
        DataTables.asMap(table)
                .forEach(
                        (path, expected) -> {
                            Object actual;
                            try {
                                actual = doc.read(path);
                            } catch (PathNotFoundException e) {
                                actual = ExpectedValueMatcher.ABSENT;
                            }
                            String reason =
                                    ExpectedValueMatcher.check(actual, ctx.resolve(expected));
                            soft.assertThat(reason).as("%s -> %s", ctx.lastCall(), path).isNull();
                        });
        soft.assertAll();
    }

    @Then("the response body does not contain {string}")
    public void theResponseBodyDoesNotContain(String text) {
        assertThat(ctx.lastResponse().rawBody())
                .as("%s -> body must not contain '%s'", ctx.lastCall(), text)
                .doesNotContain(ctx.resolve(text));
    }

    @Then("the response time is under {int} ms")
    public void theResponseTimeIsUnder(int ms) {
        assertThat(ctx.lastElapsedMs()).as("%s -> response time", ctx.lastCall()).isLessThan(ms);
    }

    private static String abbreviate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
