package com.ppln.automation.ui.pages.settings;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import com.ppln.automation.core.utils.JsRegex;
import com.ppln.automation.ui.pages.components.BaseComponent;
import io.qameta.allure.Step;
import java.util.regex.Pattern;

/**
 * Settings &gt; Alert List ({@code alert_types.html}): the alert types table ({@code
 * #alert-type-list}, columns Name, Period, Categories, Template, Action) and the add/edit form that
 * replaces it after "Add New Alert" (or a row's Edit). The inputs carry {@code aria-labelledby}
 * pointing at themselves, which hides their {@code <label>}s from {@code getByLabel}, so they are
 * found by id.
 */
public final class AlertListTab extends BaseComponent {

    private final Locator form;
    private final Locator table;

    public AlertListTab(Page page) {
        super(page, page.locator("#alerts-list"));
        form = root.locator("#alert-type-form");
        table = root.locator("#alert-type-list");
    }

    @Step("Open the Add New Alert form")
    public AlertListTab openAddForm() {
        root.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Add New Alert"))
                .click();
        return this;
    }

    public Locator nameInput() {
        return form.locator("#alert_name");
    }

    /**
     * The field (by its label text) while the browser considers it invalid; the form has no error
     * elements of its own, so constraint validation (e.g. {@code required}) is the only field-level
     * error.
     */
    public Locator invalidField(String label) {
        Locator field =
                switch (label) {
                    case "Alert Name" -> nameInput();
                    case "Alert Period" -> periodSelect();
                    case "Email Template" -> templateSelect();
                    default -> throw new IllegalArgumentException("Unknown field " + label);
                };
        return field.and(page.locator(":invalid"));
    }

    /** Clears the template choice (the select has no empty option, so no UI way exists). */
    @Step("Clear the Email Template choice")
    public AlertListTab clearTemplateChoice() {
        templateSelect().evaluate("select => { select.selectedIndex = -1; }");
        return this;
    }

    public Locator periodSelect() {
        return form.locator("#alert_period");
    }

    public Locator templateSelect() {
        return form.locator("#email_template");
    }

    @Step("Fill the alert type form: {name} / {periodLabel}")
    public AlertListTab fill(String name, String periodLabel) {
        nameInput().fill(name);
        periodSelect().selectOption(new SelectOption().setLabel(periodLabel));
        return this;
    }

    /** The value (minutes) behind a period label, as the form submits it. */
    public String periodValue(String periodLabel) {
        // CSS, not getByRole: the form is hidden (d-none) until "Add New Alert" is clicked
        return periodSelect()
                .locator("option")
                .filter(
                        new Locator.FilterOptions()
                                .setHasText(
                                        Pattern.compile(
                                                "^\\s*" + JsRegex.quote(periodLabel) + "\\s*$")))
                .getAttribute("value");
    }

    public Locator createButton() {
        return form.getByRole(
                AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Create").setExact(true));
    }

    public Locator updateButton() {
        return form.getByRole(
                AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Update").setExact(true));
    }

    /** Rows whose Name cell is exactly {@code name}. */
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

    /** Period column (2nd cell; the table has no per-cell hooks). */
    public Locator periodCell(String name) {
        return row(name).locator("td").nth(1);
    }

    /** Template column (4th cell). */
    public Locator templateCell(String name) {
        return row(name).locator("td").nth(3);
    }

    public Locator rowButton(String name, String label) {
        return row(name)
                .getByRole(
                        AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName(label).setExact(true));
    }
}
