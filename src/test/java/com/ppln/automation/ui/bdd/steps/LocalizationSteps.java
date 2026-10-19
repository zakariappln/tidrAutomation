package com.ppln.automation.ui.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import com.ppln.automation.ui.pages.components.LanguageSwitcherComponent;
import io.cucumber.java.en.When;
import java.util.Map;
import java.util.regex.Pattern;

/** The per-browser language switch (URL segment), on the login page or in the header. */
public class LocalizationSteps {

    private static final Map<String, String> CODES = Map.of("English", "en", "Japanese", "ja");

    private final UiScenarioContext ctx;

    public LocalizationSteps(UiScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("I switch the language to {string}")
    public void iSwitchTheLanguageTo(String language) {
        String name = ctx.resolve(language);
        String code = CODES.get(name);
        if (code == null) {
            throw new IllegalArgumentException(
                    "Unknown language " + name + "; known: " + CODES.keySet());
        }
        new LanguageSwitcherComponent(ctx.page()).switchTo(name);
        assertThat(ctx.page()).hasURL(Pattern.compile("^https?://[^/]+/" + code + "/.*"));
    }
}
