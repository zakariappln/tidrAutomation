package com.ppln.automation.ui.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import com.ppln.automation.core.config.ConfigManager;
import com.ppln.automation.core.utils.JsonUtils;
import com.ppln.automation.ui.auth.StorageStateManager;
import io.qameta.allure.Step;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-data set-up over HTTP with the admin's saved session ({@code StorageStateManager
 * .forRole("admin")}): the same endpoints the Users, Roles and Blocked Domains pages call, with the
 * page's CSRF token in {@code X-CSRFToken}. Used for preconditions so the UI is only driven for the
 * behaviour under test. ThreatIDR has no JSON list endpoints, so lookups parse the server-rendered
 * pages. Never asserts; callers check the returned values.
 */
public final class AdminHttp implements AutoCloseable {

    private static final Pattern CSRF =
            Pattern.compile("<meta id=\"csrf-token\" content=\"?([^\" >]+)");
    private static final Pattern USER_ID_IN_HTML = Pattern.compile("pipeline-user-lists-(\\d+)");

    private final APIRequestContext request;
    private String csrf;

    /**
     * @param playwright an open Playwright instance owned by the caller
     */
    public AdminHttp(Playwright playwright) {
        request =
                playwright
                        .request()
                        .newContext(
                                new APIRequest.NewContextOptions()
                                        .setBaseURL(ConfigManager.config().baseUrl())
                                        .setStorageStatePath(StorageStateManager.forRole("admin"))
                                        .setTimeout(ConfigManager.config().navigationTimeoutMs()));
    }

    /** A user as returned by {@code GET /en/users/update_user/<id>}. */
    public record UserRecord(
            int id,
            String email,
            String firstName,
            String lastName,
            String designation,
            String phone,
            String address,
            String about,
            Integer roleId,
            boolean active) {}

    // ---- roles ----

    public Optional<Integer> roleId(String name) {
        Matcher m =
                Pattern.compile(
                                "<tr id=\"role-list-(\\d+)\">\\s*<td class=\"primary-color\">"
                                        + Pattern.quote(name)
                                        + "</td>")
                        .matcher(usersPage(""));
        return m.find() ? Optional.of(Integer.parseInt(m.group(1))) : Optional.empty();
    }

    /** Permission keys currently granted to a role. */
    public java.util.List<String> roleAllowances(int roleId) {
        JsonNode data = json(get("/en/users/role/" + roleId)).path("data").path("allowances");
        var keys = new java.util.ArrayList<String>();
        data.forEach(n -> keys.add(n.asText()));
        return keys;
    }

    @Step("Create role {name} over HTTP")
    public APIResponse createRole(String name, Collection<String> permissionKeys) {
        return request.post("/en/users/role", withCsrf().setForm(roleForm(name, permissionKeys)));
    }

    @Step("Update role {roleId} over HTTP")
    public APIResponse updateRole(int roleId, String name, Collection<String> permissionKeys) {
        return request.put(
                "/en/users/role/" + roleId, withCsrf().setForm(roleForm(name, permissionKeys)));
    }

    @Step("Delete role {roleId} over HTTP")
    public APIResponse deleteRole(int roleId) {
        return request.delete("/en/users/role/" + roleId, withCsrf());
    }

    /** Creates the role unless one with this name exists; returns its id either way. */
    public int ensureRole(String name, Collection<String> permissionKeys) {
        Optional<Integer> existing = roleId(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        APIResponse response = createRole(name, permissionKeys);
        return roleId(name)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Role '%s' was not created: HTTP %d %s"
                                                .formatted(
                                                        name, response.status(), response.text())));
    }

    private static FormData roleForm(String name, Collection<String> permissionKeys) {
        FormData form = FormData.create().set("name", name);
        permissionKeys.forEach(k -> form.append("permissions", k));
        return form;
    }

    // ---- users ----

    /**
     * Finds a user by email. The list search only matches first/last name, so the caller passes a
     * name to search for (letters only), which keeps the result on one page.
     */
    public Optional<Integer> userId(String email, String nameToSearch) {
        String html = usersPage(nameToSearch);
        Pattern shown = Pattern.compile(">\\s*" + Pattern.quote(email) + "\\s*<");
        String[] cards = html.split("id=\"pipeline-user-lists-");
        for (int i = 1; i < cards.length; i++) {
            String card = cards[i];
            if (shown.matcher(card).find()) {
                return Optional.of(Integer.parseInt(card.substring(0, card.indexOf('"'))));
            }
        }
        return Optional.empty();
    }

    /**
     * {@code POST /en/users/register} as multipart (no photo). Keys are the form field names:
     * first_name, last_name, email, password, confirm, designation, phone, role, address, about.
     */
    @Step("Register user over HTTP")
    public APIResponse register(Map<String, String> fields) {
        FormData form = FormData.create();
        fields.forEach(form::set);
        return request.post("/en/users/register", withCsrf().setMultipart(form));
    }

    /** Registers a user and returns its id; fails with the server's answer otherwise. */
    public int createUser(Map<String, String> fields) {
        APIResponse response = register(fields);
        JsonNode body = json(response);
        Matcher m = USER_ID_IN_HTML.matcher(body.path("user").asText(""));
        if (!"Ok".equals(body.path("status").asText()) || !m.find()) {
            throw new IllegalStateException(
                    "User %s was not created: HTTP %d %s"
                            .formatted(fields.get("email"), response.status(), response.text()));
        }
        return Integer.parseInt(m.group(1));
    }

    public Optional<UserRecord> user(int id) {
        APIResponse response = get("/en/users/update_user/" + id);
        JsonNode n = json(response);
        if (!n.has("email")) {
            return Optional.empty();
        }
        return Optional.of(
                new UserRecord(
                        n.path("id").asInt(),
                        n.path("email").asText(),
                        text(n, "first_name"),
                        text(n, "last_name"),
                        text(n, "designation"),
                        text(n, "phone"),
                        text(n, "address"),
                        text(n, "about"),
                        n.path("role").isNull() ? null : n.path("role").asInt(),
                        n.path("is_active").asBoolean()));
    }

    /**
     * {@code PUT /en/users/update_user/<id>}: the view overwrites names, designation, phone,
     * address, about, role and active flag from the form, so pass the full set (see {@link
     * #editForm(UserRecord)}).
     */
    @Step("Update user {id} over HTTP")
    public APIResponse updateUser(int id, Map<String, String> form) {
        FormData data = FormData.create();
        form.forEach(data::set);
        return request.put("/en/users/update_user/" + id, withCsrf().setMultipart(data));
    }

    /** The edit form that keeps every stored value of {@code user}. */
    public static Map<String, String> editForm(UserRecord user) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("email", user.email());
        form.put("first_name", nz(user.firstName()));
        form.put("last_name", nz(user.lastName()));
        form.put("designation", nz(user.designation()));
        form.put("phone", nz(user.phone()));
        form.put("address", nz(user.address()));
        form.put("about", nz(user.about()));
        form.put("role", user.roleId() == null ? "" : user.roleId().toString());
        form.put("is_active_edit", user.active() ? "Yes" : "No");
        return form;
    }

    @Step("Delete user {id} over HTTP")
    public APIResponse deleteUser(int id) {
        if (id == 1) {
            throw new IllegalArgumentException("Refusing to delete user id 1 (the seeded admin)");
        }
        return request.delete("/en/users/update_user/" + id, withCsrf());
    }

    // ---- blocked / allowed domains ----

    /** Which list a domain lives on: its URL segment, row id prefix and Apply endpoint. */
    public enum DomainList {
        BLOCKED(
                "blocked_domains",
                "blocked_domain",
                "pipeline-blocked-domain-lists-",
                "generate_blacklist"),
        ALLOWED(
                "allowed_domains",
                "whitelisted_domain",
                "pipeline-whitelisted-domain-lists-",
                "generate_whitelist");

        private final String segment;
        private final String rowJsonField;
        private final String rowIdPrefix;
        private final String applyEndpoint;

        DomainList(String segment, String rowJsonField, String rowIdPrefix, String applyEndpoint) {
            this.segment = segment;
            this.rowJsonField = rowJsonField;
            this.rowIdPrefix = rowIdPrefix;
            this.applyEndpoint = applyEndpoint;
        }

        /** The id in the row HTML the add endpoint answers with, if the add succeeded. */
        public Optional<Integer> idInAddResponse(String responseBody) {
            JsonNode body;
            try {
                body = JsonUtils.readTree(responseBody);
            } catch (RuntimeException e) {
                return Optional.empty();
            }
            Matcher m =
                    Pattern.compile(Pattern.quote(rowIdPrefix) + "(\\d+)")
                            .matcher(body.path(rowJsonField).asText(""));
            return m.find() ? Optional.of(Integer.parseInt(m.group(1))) : Optional.empty();
        }
    }

    @Step("Add {list} domain {domain} over HTTP")
    public APIResponse addDomain(DomainList list, String domain) {
        return request.post(
                "/en/" + list.segment + "/add",
                withCsrf().setMultipart(FormData.create().set("domain", domain)));
    }

    /** Adds the domain (Inactive) and returns its id; fails with the server's answer otherwise. */
    public int createDomain(DomainList list, String domain) {
        APIResponse response = addDomain(list, domain);
        return list.idInAddResponse(response.text())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "%s domain %s was not created: HTTP %d %s"
                                                .formatted(
                                                        list,
                                                        domain,
                                                        response.status(),
                                                        abbreviate(response.text()))));
    }

    @Step("Delete {list} domain {id} over HTTP")
    public APIResponse deleteDomain(DomainList list, int id) {
        return request.delete("/en/" + list.segment + "/delete/" + id, withCsrf());
    }

    /**
     * "Apply Changes" over HTTP: activates EVERY Inactive row of the list and rewrites its RPZ zone
     * from all rows. Global effect - only call it from {@code @serial-settings} scenarios.
     */
    @Step("Apply {list} domain changes over HTTP")
    public APIResponse applyDomains(DomainList list) {
        return request.post(
                "/en/" + list.segment + "/" + list.applyEndpoint,
                withCsrf().setForm(FormData.create().set("csrf_token", csrf)));
    }

    /**
     * Id of the row holding exactly {@code domain} (case-insensitive) on any page of the list, or
     * empty. Pages are 10 rows; stops at the first page without rows.
     */
    public Optional<Integer> domainId(DomainList list, String domain) {
        Pattern row =
                Pattern.compile(
                        Pattern.quote(list.rowIdPrefix)
                                + "(\\d+)\"[^>]*>\\s*<td[^>]*>\\s*"
                                + Pattern.quote(htmlEscape(domain))
                                + "\\s*</td>",
                        Pattern.CASE_INSENSITIVE);
        for (int page = 1; page <= 100; page++) {
            String html = request.get("/en/" + list.segment + "/?page=" + page).text();
            if (!html.contains(list.rowIdPrefix)) {
                return Optional.empty();
            }
            Matcher m = row.matcher(html);
            if (m.find()) {
                return Optional.of(Integer.parseInt(m.group(1)));
            }
        }
        return Optional.empty();
    }

    // ---- threats ----

    /**
     * The newest threat of the last 30 days as the Threat Detection table's own data call returns
     * it ({@code POST /en/threats/data}, newest first, one row): {@code query_domain}, {@code
     * threat_type}, ... Missing when there is none. Reading it this way does not mark threats as
     * viewed, unlike opening the Threat Detection page.
     */
    public Optional<JsonNode> newestThreat() {
        FormData form =
                FormData.create()
                        .set("draw", "1")
                        .set("start", "0")
                        .set("length", "1")
                        .set("search[value]", "")
                        .set("threat", "")
                        .set("risk_category", "")
                        .set("date", "")
                        .set("domain_count", "")
                        .set("group_by", "")
                        .set("short_by", "desc");
        JsonNode rows =
                json(request.post("/en/threats/data", withCsrf().setForm(form))).path("data");
        return rows.isArray() && !rows.isEmpty() ? Optional.of(rows.get(0)) : Optional.empty();
    }

    // ---- settings (views/setting/views.py) ----

    /**
     * The server-rendered Settings page. Note that rendering it has side effects in the app: it
     * inserts default rows for missing settings and re-saves the stored response policy.
     */
    public String settingsHtml() {
        APIResponse response = get("/en/settings/");
        if (!response.url().contains("/settings")) {
            throw new IllegalStateException(
                    "Admin session not accepted: settings ended at " + response.url());
        }
        return response.text();
    }

    /** {@code POST /en/settings/general-settings}: default language and timezone (global). */
    @Step("Save general settings {language} / {timezone} over HTTP")
    public APIResponse saveGeneralSettings(String language, String timezone) {
        return request.post(
                "/en/settings/general-settings",
                withCsrf()
                        .setForm(
                                FormData.create()
                                        .set("default_lang", language)
                                        .set("default_timezone", timezone)));
    }

    /**
     * {@code POST /en/settings/alert-settings}. {@code emailField} is sent as is: the page sends
     * Tagify JSON ({@code [{"value":"a@b"}]}) or an empty string.
     */
    @Step("Save alert settings over HTTP")
    public APIResponse saveAlertSettings(String emailField, boolean notificationBell) {
        FormData form = FormData.create().set("email", emailField);
        if (notificationBell) {
            form.set("is_notification_bell_enable", "y");
        }
        return request.post("/en/settings/alert-settings", withCsrf().setForm(form));
    }

    /** {@code POST /en/settings/dns-bypass}: rewrites the bypass zone file and reloads it. */
    @Step("Set DNS bypass enabled={enabled} over HTTP")
    public APIResponse setDnsBypass(boolean enabled) {
        return request.post(
                "/en/settings/dns-bypass",
                withCsrf()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of("status", enabled ? "Enable" : "Disable")));
    }

    public Optional<Integer> alertTypeId(String name) {
        return idInRow(settingsHtml(), "alert-type-list-", name);
    }

    public Optional<Integer> emailTemplateId(String name) {
        return idInRow(settingsHtml(), "email-template-list-item-", name);
    }

    /** {@code POST /en/settings/alert-types}; {@code period} is the option value in minutes. */
    @Step("Create alert type {name} over HTTP")
    public APIResponse createAlertType(String name, String period, int emailTemplateId) {
        return request.post(
                "/en/settings/alert-types",
                withCsrf()
                        .setForm(
                                FormData.create()
                                        .set("alert_name", name)
                                        .set("alert_period", period)
                                        .set("email_template", Integer.toString(emailTemplateId))));
    }

    /** The alert type as its edit form loads it ({@code GET /en/settings/alert-types/<id>}). */
    public JsonNode alertType(int id) {
        return json(get("/en/settings/alert-types/" + id)).path("data");
    }

    @Step("Delete alert type {id} over HTTP")
    public APIResponse deleteAlertType(int id) {
        return request.delete("/en/settings/alert-types/" + id, withCsrf());
    }

    @Step("Create email template {name} over HTTP")
    public APIResponse createEmailTemplate(String name, String content) {
        return request.post(
                "/en/settings/email-templates",
                withCsrf()
                        .setMultipart(
                                FormData.create()
                                        .set("template_name", name)
                                        .set("content", content)));
    }

    /** The stored content of an email template, as its edit form loads it. */
    public String emailTemplateContent(int id) {
        return json(get("/en/settings/email-templates/" + id))
                .path("data")
                .path("content")
                .asText();
    }

    /** Whether the Settings page still lists the email template with this id. */
    public boolean emailTemplateExists(int id) {
        return settingsHtml().contains("id=\"email-template-list-item-" + id + "\"");
    }

    /** Whether the Settings page still lists the alert type with this id. */
    public boolean alertTypeExists(int id) {
        return settingsHtml().contains("id=\"alert-type-list-" + id + "\"");
    }

    @Step("Delete email template {id} over HTTP")
    public APIResponse deleteEmailTemplate(int id) {
        return request.delete("/en/settings/email-templates/" + id, withCsrf());
    }

    private static Optional<Integer> idInRow(String html, String rowIdPrefix, String firstCell) {
        Matcher m =
                Pattern.compile(
                                "<tr id=\""
                                        + Pattern.quote(rowIdPrefix)
                                        + "(\\d+)\">\\s*<td>\\s*"
                                        + Pattern.quote(htmlEscape(firstCell))
                                        + "\\s*</td>")
                        .matcher(html);
        return m.find() ? Optional.of(Integer.parseInt(m.group(1))) : Optional.empty();
    }

    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&#34;")
                .replace("'", "&#39;");
    }

    // ---- plumbing ----

    public APIResponse get(String path) {
        return request.get(path);
    }

    private String usersPage(String search) {
        String path =
                search.isBlank()
                        ? "/en/users/"
                        : "/en/users/?search=" + URLEncoder.encode(search, StandardCharsets.UTF_8);
        APIResponse response = get(path);
        if (!response.url().contains("/users")) {
            throw new IllegalStateException(
                    "Admin session not accepted: GET %s ended at %s"
                            .formatted(path, response.url()));
        }
        return response.text();
    }

    /**
     * The token comes from the admin's own profile page: the Users page also carries it, but it
     * renders every user and fails (HTTP 500) while another request creates or deletes one.
     */
    private RequestOptions withCsrf() {
        if (csrf == null) {
            APIResponse profile = get("/en/user/profile");
            Matcher m = CSRF.matcher(profile.text());
            if (!m.find()) {
                throw new IllegalStateException(
                        "No csrf-token meta on %s (HTTP %d)"
                                .formatted(profile.url(), profile.status()));
            }
            csrf = m.group(1);
        }
        return RequestOptions.create().setHeader("X-CSRFToken", csrf);
    }

    private static JsonNode json(APIResponse response) {
        try {
            return JsonUtils.readTree(response.text());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Expected JSON from %s, got HTTP %d: %s"
                            .formatted(
                                    response.url(), response.status(), abbreviate(response.text())),
                    e);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isNull() || v.isMissingNode() ? null : v.asText();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String abbreviate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    @Override
    public void close() {
        request.dispose();
    }
}
