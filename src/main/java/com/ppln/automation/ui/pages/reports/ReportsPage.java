package com.ppln.automation.ui.pages.reports;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import com.ppln.automation.ui.pages.BasePage;
import io.qameta.allure.Step;
import java.time.LocalDate;

/**
 * Reports ({@code /<lang_code>/reports/}). Confirmed live 2026-10-17: the quick-range controls
 * (Today / Yesterday / Last 7 Day / Last 30 Day) are plain {@code <span class="badge">} elements
 * with no role, so they are located by exact text; the date inputs are {@code input[type=date]}
 * with ids {@code fromDate} / {@code toDate} and no associated label, so they are located by id.
 *
 * <p>Report Format is {@code select#report_type} (values {@code summary}, {@code csv}) and the
 * Generate button is {@code #Genbutton}; ids rather than labels because the page is also used under
 * {@code /ja/}, where every label is translated. The date inputs carry min (30 days ago) and max
 * (today) attributes, but the page never validates them before sending (report.js), so {@link
 * #setRange} types any date the way a user can.
 */
public final class ReportsPage extends BasePage {

    private final String lang;
    private final Locator fromDate;
    private final Locator toDate;
    private final Locator reportFormat;
    private final Locator generateButton;

    public ReportsPage(Page page) {
        this(page, "en");
    }

    /** The page under {@code /<lang>/reports/}. */
    public ReportsPage(Page page, String lang) {
        super(page);
        this.lang = lang;
        fromDate = page.locator("#fromDate");
        toDate = page.locator("#toDate");
        reportFormat = page.locator("#report_type");
        generateButton = page.locator("#Genbutton");
    }

    @Override
    protected String path() {
        return "/" + lang + "/reports/";
    }

    @Step("Click quick range {name}")
    public ReportsPage clickQuickRange(String name) {
        page.getByText(name, new Page.GetByTextOptions().setExact(true)).click();
        return this;
    }

    @Step("Set the report range {from} - {to}")
    public ReportsPage setRange(LocalDate from, LocalDate to) {
        fromDate.fill(from.toString());
        toDate.fill(to.toString());
        return this;
    }

    /** {@code value}: the option value, {@code summary} or {@code csv}. */
    @Step("Choose report format {value}")
    public ReportsPage chooseFormat(String value) {
        reportFormat.selectOption(new SelectOption().setValue(value));
        return this;
    }

    @Step("Generate the report")
    public ReportsPage generate() {
        generateButton.click();
        return this;
    }

    /** {@code label}: the option text as shown, "Summary" or "CSV". */
    @Step("Choose report format {label}")
    public ReportsPage chooseFormatLabelled(String label) {
        reportFormat.selectOption(new SelectOption().setLabel(label));
        return this;
    }

    public Locator fromDate() {
        return fromDate;
    }

    public Locator toDate() {
        return toDate;
    }

    public Locator reportFormat() {
        return reportFormat;
    }
}
