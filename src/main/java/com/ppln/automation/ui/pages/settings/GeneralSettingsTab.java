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
 * Settings &gt; General Settings ({@code templates/setting/partials/alert_settings/general.html}):
 * language/timezone form, RPZ Zone Settings (one {@code <select
 * name="<Category_with_underscores>">} per threat category) and Access Control (Tagify over {@code
 * #allowed-dns-ip}).
 *
 * <p>The language/timezone labels point at a non-existent {@code for="mail_use"}, so those selects
 * are found by id. Category select names contain {@code &} ("Command_&_Control"), so they are
 * matched by attribute, not by an id selector.
 */
public final class GeneralSettingsTab extends BaseComponent {

    private final Locator languageForm;
    private final Locator rpzForm;
    private final Locator accessControlForm;
    private final Locator networkTags;

    public GeneralSettingsTab(Page page) {
        super(page, page.locator("#general-settings-1"));
        languageForm = root.locator("#general-form");
        rpzForm = root.locator("#general-bind-settings-policy");
        accessControlForm = root.locator("#general-bind-settings-ip");
        networkTags = accessControlForm.locator("tags.tagify");
    }

    public Locator defaultLanguage() {
        return languageForm.locator("#default_lang");
    }

    public Locator defaultTimezone() {
        return languageForm.locator("#default_timezone");
    }

    @Step("Choose default language {label} and timezone {timezone}")
    public GeneralSettingsTab chooseLanguageAndTimezone(String label, String timezone) {
        defaultLanguage().selectOption(new SelectOption().setLabel(label));
        defaultTimezone().selectOption(timezone);
        return this;
    }

    public Locator languageSaveButton() {
        return save(languageForm);
    }

    /** The action select of an RPZ category, by its visible name, e.g. "User Allowed". */
    public Locator rpzAction(String category) {
        return rpzForm.locator("select[name='" + category.replace(' ', '_') + "']");
    }

    /** The option of the category's select that is marked {@code selected} in the HTML. */
    public Locator rpzSelectedOption(String category) {
        return rpzAction(category).locator("option[selected]");
    }

    public Locator rpzOption(String category, String label) {
        return rpzAction(category)
                .locator("option")
                .filter(
                        new Locator.FilterOptions()
                                .setHasText(
                                        Pattern.compile("^\\s*" + JsRegex.quote(label) + "\\s*$")));
    }

    /** Tags currently in "Enter IP to Allow DNS Queries". */
    public Locator networkTags() {
        return networkTags.locator("tag.tagify__tag");
    }

    public Locator networkTag(String value) {
        return networkTags()
                .filter(
                        new Locator.FilterOptions()
                                .setHas(
                                        page.locator(".tagify__tag-text")
                                                .getByText(
                                                        value,
                                                        new Locator.GetByTextOptions()
                                                                .setExact(true))));
    }

    @Step("Add {value} to Allow DNS Queries")
    public GeneralSettingsTab addNetwork(String value) {
        Locator input = networkTags.locator(".tagify__input");
        input.click();
        input.pressSequentially(value);
        input.press("Enter");
        return this;
    }

    @Step("Remove {value} from Allow DNS Queries")
    public GeneralSettingsTab removeNetwork(String value) {
        networkTag(value).locator(".tagify__tag__removeBtn").click();
        return this;
    }

    /**
     * The original (hidden, {@code required}) Access Control input while it is empty: the browser's
     * constraint validation then blocks the submit without any visible message.
     */
    public Locator emptyNetworkField() {
        return accessControlForm.locator("#allowed-dns-ip:invalid");
    }

    public Locator accessControlSaveButton() {
        return save(accessControlForm);
    }

    private static Locator save(Locator form) {
        return form.getByRole(
                AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save").setExact(true));
    }
}
