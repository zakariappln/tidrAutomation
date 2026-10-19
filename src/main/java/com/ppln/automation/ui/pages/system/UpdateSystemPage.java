package com.ppln.automation.ui.pages.system;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.ppln.automation.ui.pages.BasePage;

/**
 * Update System ({@code /<lang>/update}, {@code templates/home/update.html}). Its settings tab is
 * commented out, but the page and its upload form are live. The server answers an upload with a
 * redirect back here and a flashed message, rendered as {@code div#message.alert}.
 */
public final class UpdateSystemPage extends BasePage {

    private final Locator message;

    public UpdateSystemPage(Page page) {
        super(page);
        message = page.locator("#message");
    }

    @Override
    protected String path() {
        return "/en/update";
    }

    /** The flashed upload outcome (success or error alert). */
    public Locator message() {
        return message;
    }
}
