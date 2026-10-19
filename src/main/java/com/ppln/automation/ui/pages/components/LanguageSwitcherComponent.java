package com.ppln.automation.ui.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

/**
 * The language dropdown of the login page (templates/home/login.html) and of the authenticated
 * header (templates/nav.html). Both are a {@code #languageDropdown} toggle whose menu holds one
 * link per language pointing at the same route under {@code /<lang>/}; the entries read "English" /
 * "Japanese" on the login page and "English (English)" / "Japanese (日本語)" in the header. A switch
 * only changes the URL's language segment for this browser; it never touches the global default
 * language (Settings).
 */
public final class LanguageSwitcherComponent extends BaseComponent {

    private final Locator toggle;

    public LanguageSwitcherComponent(Page page) {
        super(page, page.locator("[aria-labelledby='languageDropdown']"));
        toggle = page.locator("#languageDropdown");
    }

    /** {@code language}: the entry's English name, "English" or "Japanese". */
    @Step("Switch the language to {language}")
    public void switchTo(String language) {
        toggle.click();
        root.locator("a").filter(new Locator.FilterOptions().setHasText(language)).first().click();
    }
}
