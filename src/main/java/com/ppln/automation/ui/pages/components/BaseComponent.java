package com.ppln.automation.ui.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/** Reusable widget scoped to a root locator; every child locator is derived from {@link #root}. */
public abstract class BaseComponent {

    protected final Page page;
    protected final Locator root;

    protected BaseComponent(Page page, Locator root) {
        this.page = page;
        this.root = root;
    }

    public Locator root() {
        return root;
    }
}
