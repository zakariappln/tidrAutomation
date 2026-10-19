package com.ppln.automation.api.bdd;

import com.ppln.automation.core.bdd.CucumberTagFilter;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Runs {@code features/api/**} through TestNG as part of the {@code api} group. {@code
 * -Dgroups=...} is added at runtime by {@code AnnotationTransformer} and mapped to a Cucumber tag
 * expression by {@link CucumberTagFilter}.
 */
@Test(groups = {"api"})
@CucumberOptions(
        features = "src/test/resources/features/api",
        glue = "com.ppln.automation.api.bdd",
        plugin = {
            "pretty",
            "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",
            "json:target/cucumber/api.json"
        },
        snippets = CucumberOptions.SnippetType.CAMELCASE)
public class RunApiCucumberTest extends AbstractTestNGCucumberTests {

    static {
        CucumberTagFilter.apply();
    }

    /** Scenario-level parallelism; safe because all state is per-scenario (PicoContainer). */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
