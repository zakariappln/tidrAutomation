package com.ppln.automation.ui.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * SweetAlert2 popups: the app's {@code Toast} mixin (toast, 2 s timer, top-end) and plain {@code
 * Swal.fire} dialogs both render a {@code .swal2-popup}. Pages use them for every save/delete
 * outcome.
 */
public final class MessagesComponent extends BaseComponent {

    public MessagesComponent(Page page) {
        super(page, page.locator(".swal2-popup"));
    }

    /** A toast or dialog containing {@code text}. */
    public Locator withText(String text) {
        return root.filter(new Locator.FilterOptions().setHasText(text));
    }

    /** A toast or dialog whose text matches {@code text}. */
    public Locator withText(java.util.regex.Pattern text) {
        return root.filter(new Locator.FilterOptions().setHasText(text));
    }

    /**
     * A flashed server message rendered as a Bootstrap alert ({@code .alert[role=alert]}), the way
     * server-rendered pages such as History of IP show validation errors.
     */
    public Locator flashedAlert(String text) {
        return page.locator(".alert[role='alert']")
                .filter(new Locator.FilterOptions().setHasText(text));
    }

    /** The confirm button of the open dialog (e.g. "Download" after a report was generated). */
    public Locator confirmButton() {
        return root.locator(".swal2-confirm");
    }
}
