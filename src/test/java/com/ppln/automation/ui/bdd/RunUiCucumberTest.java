package com.ppln.automation.ui.bdd;

import com.ppln.automation.core.bdd.CucumberTagFilter;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Runs {@code features/ui/**} through TestNG. Static groups keep the runner inside the default UI
 * suite; {@code -Dgroups=...} is added at runtime by {@code AnnotationTransformer} and mapped to a
 * Cucumber tag expression by {@link CucumberTagFilter}.
 */
@Test(groups = {"smoke", "regression"})
@CucumberOptions(
        features = "src/test/resources/features/ui",
        glue = "com.ppln.automation.ui.bdd",
        plugin = {
            "pretty",
            "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",
            "json:target/cucumber/ui.json"
        },
        snippets = CucumberOptions.SnippetType.CAMELCASE)
public class RunUiCucumberTest extends AbstractTestNGCucumberTests {

    static {
        CucumberTagFilter.apply();
    }

    /** Scenario-level parallelism; safe because every scenario owns its browser objects. */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
