package com.ppln.automation.ui.fixtures;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The 15 permissions a role can be granted from the Roles form, label to key, in form order
 * (source: views/user/forms.py RoleForm). {@code *}, {@code dga-active} and {@code backup-active}
 * are not offered there, so a custom role can never hold them.
 */
public final class PermissionCatalog {

    private static final Map<String, String> BY_LABEL = new LinkedHashMap<>();

    static {
        BY_LABEL.put("Read Reports", "report-read");
        BY_LABEL.put("Export Reports", "report-export");
        BY_LABEL.put("Update Settings", "setting-update");
        BY_LABEL.put("Create Users", "user-create");
        BY_LABEL.put("Update Users", "user-update");
        BY_LABEL.put("Delete Users", "user-delete");
        BY_LABEL.put("Create Roles", "role-create");
        BY_LABEL.put("Update Roles", "role-update");
        BY_LABEL.put("Delete Roles", "role-delete");
        BY_LABEL.put("Create Alert Types", "alerttype-create");
        // the form's own label has a typo ("Update ALert Types"); both spellings are accepted
        BY_LABEL.put("Update Alert Types", "alerttype-update");
        BY_LABEL.put("Delete Alert Types", "alerttype-delete");
        BY_LABEL.put("Create Email Templates", "emailtemplate-create");
        BY_LABEL.put("Update Email Templates", "emailtemplate-update");
        BY_LABEL.put("Delete Email Templates", "emailtemplate-delete");
    }

    private PermissionCatalog() {}

    /** Permission key for a form label, case-insensitive, e.g. "Read Reports" to report-read. */
    public static String keyFor(String label) {
        String wanted = label.trim().toLowerCase(Locale.ROOT);
        return BY_LABEL.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase(Locale.ROOT).equals(wanted))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unknown permission label '%s'; known: %s"
                                                .formatted(label, BY_LABEL.keySet())));
    }

    /** Every key a custom role can hold, in form order. */
    public static List<String> grantableKeys() {
        return Collections.unmodifiableList(List.copyOf(BY_LABEL.values()));
    }
}
