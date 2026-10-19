package com.ppln.automation.ui.pages.settings;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.ppln.automation.ui.pages.components.BaseComponent;
import io.qameta.allure.Step;

/**
 * Settings &gt; System Log ({@code syslog.html}): the "Syslog Configuration" form. Both check boxes
 * carry a label pointing at {@code #forwardSyslog} (the TCP label is wrong), so they are found by
 * id; the text fields by their labels.
 */
public final class SyslogTab extends BaseComponent {

    public SyslogTab(Page page) {
        super(page, page.locator("#syslog"));
    }

    public Locator serverIp() {
        return root.getByLabel("Server IP");
    }

    public Locator serverPort() {
        return root.getByLabel("Server Port");
    }

    public Locator overTcp() {
        return root.locator("#socktype");
    }

    @Step("Fill the syslog target {ip}:{port}, TCP={tcp}")
    public SyslogTab fill(String ip, String port, boolean tcp) {
        serverIp().fill(ip);
        serverPort().fill(port);
        overTcp().setChecked(tcp);
        return this;
    }

    public Locator saveButton() {
        return root.getByRole(
                AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save").setExact(true));
    }
}
