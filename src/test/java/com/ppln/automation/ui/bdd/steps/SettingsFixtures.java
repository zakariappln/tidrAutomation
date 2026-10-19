package com.ppln.automation.ui.bdd.steps;

import com.microsoft.playwright.APIResponse;
import com.ppln.automation.ui.bdd.context.UiScenarioContext;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Alert type and email template preconditions and cleanups (admin over HTTP), shared by the Alert
 * List and Email Template steps. Cleanup looks the record up by its unique name when it runs, so it
 * also removes a record a UI step created without the step learning its id; LIFO order removes an
 * alert type before the template it references (the FK has no ON DELETE).
 */
final class SettingsFixtures {

    static final String DEFAULT_TEMPLATE = "Default";
    private static final int MAX_DUPLICATES = 5;
    private static final Pattern ALERT_TYPE_ROW = Pattern.compile("alert-type-list-(\\d+)");
    private static final Pattern TEMPLATE_ROW = Pattern.compile("email-template-list-item-(\\d+)");

    private SettingsFixtures() {}

    /** Deletes, at cleanup, every alert type still named {@code name}. */
    static void deleteAlertTypeAfterwards(UiScenarioContext ctx, String name) {
        ctx.onCleanup(
                () -> {
                    Optional<Integer> id;
                    for (int i = 0;
                            i < MAX_DUPLICATES && (id = ctx.admin().alertTypeId(name)).isPresent();
                            i++) {
                        expectOk(
                                ctx.admin().deleteAlertType(id.get()), "delete alert type " + name);
                    }
                });
    }

    /** Deletes, at cleanup, every email template still named {@code name}. */
    static void deleteEmailTemplateAfterwards(UiScenarioContext ctx, String name) {
        ctx.onCleanup(
                () -> {
                    Optional<Integer> id;
                    for (int i = 0;
                            i < MAX_DUPLICATES
                                    && (id = ctx.admin().emailTemplateId(name)).isPresent();
                            i++) {
                        expectOk(
                                ctx.admin().deleteEmailTemplate(id.get()),
                                "delete email template " + name);
                    }
                });
    }

    /**
     * Creates an alert type (period "Every 5 minutes") and returns its id; deleted by id in cleanup
     * (so a rename does not hide it).
     */
    static int createAlertType(UiScenarioContext ctx, String name, int templateId) {
        APIResponse response = ctx.admin().createAlertType(name, "5", templateId);
        int id = idIn(response, ALERT_TYPE_ROW, "alert type " + name);
        ctx.onCleanup(
                () -> {
                    if (ctx.admin().alertTypeExists(id)) {
                        expectOk(ctx.admin().deleteAlertType(id), "delete alert type " + id);
                    }
                });
        return id;
    }

    /** Creates an email template and returns its id; deleted by id in cleanup. */
    static int createEmailTemplate(UiScenarioContext ctx, String name, String content) {
        APIResponse response = ctx.admin().createEmailTemplate(name, content);
        int id = idIn(response, TEMPLATE_ROW, "email template " + name);
        ctx.onCleanup(
                () -> {
                    if (ctx.admin().emailTemplateExists(id)) {
                        expectOk(
                                ctx.admin().deleteEmailTemplate(id), "delete email template " + id);
                    }
                });
        return id;
    }

    static int templateId(UiScenarioContext ctx, String name) {
        return ctx.admin()
                .emailTemplateId(name)
                .orElseThrow(() -> new IllegalStateException("No email template named " + name));
    }

    private static int idIn(APIResponse response, Pattern rowId, String what) {
        Matcher m = rowId.matcher(response.text());
        if (response.status() != 200 || !m.find()) {
            throw new IllegalStateException(
                    "%s was not created: HTTP %d %s"
                            .formatted(what, response.status(), response.text()));
        }
        return Integer.parseInt(m.group(1));
    }

    private static void expectOk(APIResponse response, String what) {
        if (response.status() != 200) {
            throw new IllegalStateException(
                    "%s: HTTP %d %s".formatted(what, response.status(), response.text()));
        }
    }
}
