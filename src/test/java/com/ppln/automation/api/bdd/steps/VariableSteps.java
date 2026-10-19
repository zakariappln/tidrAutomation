package com.ppln.automation.api.bdd.steps;

import com.jayway.jsonpath.JsonPath;
import com.ppln.automation.api.bdd.context.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.Locale;

/**
 * Captures values from the last response for later steps: {@code store "$.data.id" as "branchId"}.
 */
public class VariableSteps {

    private final ScenarioContext ctx;

    public VariableSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    /**
     * {@code Given "not-a-guid" is stored as "recordId"}. Placeholders in the value are resolved;
     * anything else (deliberately malformed ids) is stored verbatim.
     */
    @Given("{string} is stored as {string}")
    public void isStoredAs(String value, String name) {
        ctx.put(name, ctx.resolve(value));
    }

    @Then("store {string} as {string}")
    public void storeAs(String jsonPath, String name) {
        Object value = JsonPath.parse(ctx.lastResponse().rawBody()).read(jsonPath);
        if (value == null) {
            throw new AssertionError(
                    ctx.lastCall() + " -> cannot store " + jsonPath + ": value is null");
        }
        ctx.put(name, String.valueOf(value));
    }

    @Then("store header {string} as {string}")
    public void storeHeaderAs(String header, String name) {
        String value = ctx.lastResponse().headers().get(header.toLowerCase(Locale.ROOT));
        if (value == null) {
            throw new AssertionError(
                    ctx.lastCall()
                            + " -> header '"
                            + header
                            + "' absent, cannot store as {"
                            + name
                            + "}");
        }
        ctx.put(name, value);
    }
}
