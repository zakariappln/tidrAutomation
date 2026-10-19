package com.ppln.automation.ui.pages.settings;

import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.components.BaseComponent;
import io.qameta.allure.Step;

/**
 * Settings &gt; Email Template ({@code email_templates.html}): the templates table ({@code
 * #email-template-list}) and the add/edit form with a CKEditor 4 body ({@code textarea#content}
 * replaced by an iframe editor). Opening a template means its Edit button: the View action is
 * commented out in this build.
 */
public final class EmailTemplatesTab extends BaseComponent {

    private final Locator form;
    private final Locator table;

    public EmailTemplatesTab(Page page) {
        super(page, page.locator("#email-template"));
        form = root.locator("#email-template-form");
        table = root.locator("#email-template-list");
    }

    @Step("Open the Add New Template form")
    public EmailTemplatesTab openAddForm() {
        root.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Add New Template"))
                .click();
        return this;
    }

    public Locator nameInput() {
        // aria-labelledby points at the input itself, which hides its <label> from getByLabel
        return form.locator("#template_name");
    }

    /** The editable body inside CKEditor's iframe. */
    public Locator editorBody() {
        return editorFrame().locator("body");
    }

    public FrameLocator editorFrame() {
        return form.frameLocator("iframe.cke_wysiwyg_frame");
    }

    /** A CKEditor toolbar button by its command class, e.g. "bold", "bulletedlist", "source". */
    public Locator toolbarButton(String command) {
        return form.locator("a.cke_button__" + command);
    }

    /** The plain-text area CKEditor shows in Source mode. */
    public Locator sourceArea() {
        return form.locator("textarea.cke_source");
    }

    /** Places the caret in the editor; typing then goes to the caret position. */
    @Step("Focus the template body")
    public EmailTemplatesTab focusBody() {
        editorBody().click();
        return this;
    }

    /**
     * Types at the caret through the keyboard (locator-based typing would re-focus the body and
     * move the caret, splitting list items).
     */
    @Step("Type {text} into the template body")
    public EmailTemplatesTab typeIntoBody(String text) {
        page.keyboard().type(text);
        return this;
    }

    public EmailTemplatesTab pressInBody(String key) {
        page.keyboard().press(key);
        return this;
    }

    public Locator createButton() {
        return form.getByRole(
                AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Create").setExact(true));
    }

    public Locator updateButton() {
        return form.getByRole(
                AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Update").setExact(true));
    }

    public Locator row(String name) {
        return table.locator("tbody tr")
                .filter(
                        new Locator.FilterOptions()
                                .setHas(
                                        page.getByRole(
                                                AriaRole.CELL,
                                                new Page.GetByRoleOptions()
                                                        .setName(name)
                                                        .setExact(true))));
    }

    public Locator rowButton(String name, String label) {
        return row(name)
                .getByRole(
                        AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName(label).setExact(true));
    }
}
