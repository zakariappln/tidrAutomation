package com.ppln.automation.ui.pages.settings;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.components.BaseComponent;
import io.qameta.allure.Step;

/**
 * Settings &gt; Alert Settings ({@code alert_config.html}): "Recipient Email" is a Tagify field
 * over {@code #multiple-email} (submitted as Tagify JSON), plus the "System Notification Sound"
 * box, whose checked state comes from the browser's localStorage, not from the server.
 */
public final class AlertSettingsTab extends BaseComponent {

    private final Locator form;
    private final Locator emailTags;

    public AlertSettingsTab(Page page) {
        super(page, page.locator("#alert-settings-1"));
        form = root.locator("#alert-form");
        emailTags = form.locator("tags.tagify");
    }

    public Locator emailTags() {
        return emailTags.locator("tag.tagify__tag");
    }

    @Step("Add notification email {value}")
    public AlertSettingsTab addEmail(String value) {
        Locator input = emailTags.locator(".tagify__input");
        input.click();
        input.pressSequentially(value);
        input.press("Enter");
        return this;
    }

    @Step("Remove every notification email")
    public AlertSettingsTab clearEmails() {
        int count = emailTags().count();
        for (int i = 0; i < count; i++) {
            emailTags().first().locator(".tagify__tag__removeBtn").click();
        }
        return this;
    }

    public Locator saveButton() {
        return form.getByRole(
                AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save").setExact(true));
    }
}
