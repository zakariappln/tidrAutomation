package com.ppln.automation.ui.fixtures;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.ppln.automation.core.config.Credentials;
import com.ppln.automation.ui.auth.StorageStateManager;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Limited-permission accounts for authorization scenarios: one role and one user per permission
 * set, created as admin over HTTP and <em>kept</em> between runs (names are deterministic, so a
 * later run finds and reuses them instead of spending login slots on new accounts). Their passwords
 * live in the git-ignored {@code <auth.state.dir>/<env>/fixture-users.properties}; if that file is
 * lost the user is recreated. Sessions are cached by {@link StorageStateManager} under {@code
 * fixture-<slug>}, so each account costs one real login per session lifetime.
 *
 * <p>Names: role {@code qa-auto-<slug>}, user {@code qa-auto-<slug>@example.com}, first name
 * "Qafixture", last name "Qafix" plus the slug's letters (the list search matches names only, and
 * the create form accepts letters only).
 *
 * <p>Disposable users ({@link #createDisposable}) are the per-scenario variant for tests that
 * change the account itself (password change); the caller deletes them.
 */
public final class FixtureUsers {

    private static final Logger log = LoggerFactory.getLogger(FixtureUsers.class);
    private static final String PASSWORDS_FILE = "fixture-users.properties";
    private static final String PREFIX = "qa-auto-";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ConcurrentMap<String, Fixture> READY = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();

    /** Fixture role ids resolved in this JVM (the roles are persistent, ids never change). */
    private static final ConcurrentMap<String, Integer> ROLE_IDS = new ConcurrentHashMap<>();

    /** A ready account: its saved-session name, credentials, user id and role id. */
    public record Fixture(
            String sessionName,
            Credentials credentials,
            int userId,
            int roleId,
            Path storageState) {}

    /** A per-scenario account created by {@link #createDisposable}. */
    public record Disposable(int userId, Credentials credentials) {}

    private FixtureUsers() {}

    /** The account holding exactly the permissions with these form labels. */
    public static Fixture withPermissions(List<String> labels) {
        List<String> keys = labels.stream().map(PermissionCatalog::keyFor).sorted().toList();
        String slug = keys.size() == 1 ? keys.get(0) : String.join("-", keys);
        return ensure(slug, keys);
    }

    /** The account holding all 15 permissions the Roles form can grant. */
    public static Fixture withEveryGrantablePermission() {
        return ensure("all", PermissionCatalog.grantableKeys());
    }

    private static Fixture ensure(String slug, List<String> keys) {
        Fixture fixture = READY.get(slug);
        if (fixture == null) {
            synchronized (LOCK) {
                fixture = READY.computeIfAbsent(slug, s -> create(s, keys));
            }
        }
        // re-probed / re-signed by StorageStateManager on every call
        Path state =
                StorageStateManager.forCredentials(fixture.sessionName(), fixture.credentials());
        return new Fixture(
                fixture.sessionName(),
                fixture.credentials(),
                fixture.userId(),
                fixture.roleId(),
                state);
    }

    private static Fixture create(String slug, List<String> keys) {
        String roleName = PREFIX + slug;
        String email = PREFIX + slug + "@example.com";
        if (email.length() > 40) {
            throw new IllegalArgumentException(
                    "Fixture email %s exceeds the 40-character limit of the user form"
                            .formatted(email));
        }
        String lastName = "Qafix" + slug.replaceAll("[^a-z]", "");
        Credentials credentials;
        int userId;
        int roleId;
        try (Playwright playwright = Playwright.create();
                AdminHttp admin = new AdminHttp(playwright)) {
            roleId = admin.ensureRole(roleName, keys);
            if (!new HashSet<>(admin.roleAllowances(roleId)).equals(new HashSet<>(keys))) {
                admin.updateRole(roleId, roleName, keys);
            }
            Properties passwords = loadPasswords();
            String password = passwords.getProperty(email);
            Optional<Integer> existing = admin.userId(email, lastName);
            if (existing.isPresent() && password == null) {
                log.info(
                        "Fixture user {} exists but its password is unknown; recreating it", email);
                admin.deleteUser(existing.get());
                existing = Optional.empty();
            }
            if (existing.isPresent()) {
                userId = existing.get();
                repairRole(admin, userId, roleId);
                log.info("Reusing fixture user {} (id {}) with role {}", email, userId, roleName);
            } else {
                password = newPassword();
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("first_name", "Qafixture");
                fields.put("last_name", lastName);
                fields.put("email", email);
                fields.put("password", password);
                fields.put("confirm", password);
                fields.put("designation", "QA Fixture");
                fields.put("phone", "+8801812345678");
                fields.put("role", Integer.toString(roleId));
                userId = admin.createUser(fields);
                passwords.setProperty(email, password);
                savePasswords(passwords);
                log.info("Created fixture user {} (id {}) with role {}", email, userId, roleName);
            }
            credentials = new Credentials(email, password);
        }
        return new Fixture("fixture-" + slug, credentials, userId, roleId, null);
    }

    /** Keeps a reused account on its role and active (a scenario may have edited it). */
    private static void repairRole(AdminHttp admin, int userId, int roleId) {
        AdminHttp.UserRecord user =
                admin.user(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Fixture user " + userId + " vanished"));
        if (Integer.valueOf(roleId).equals(user.roleId()) && user.active()) {
            return;
        }
        Map<String, String> form = AdminHttp.editForm(user);
        form.put("role", Integer.toString(roleId));
        form.put("is_active_edit", "Yes");
        APIResponse response = admin.updateUser(userId, form);
        if (response.status() != 200) {
            throw new IllegalStateException(
                    "Could not repair fixture user %d: HTTP %d %s"
                            .formatted(userId, response.status(), response.text()));
        }
    }

    /**
     * Creates a throw-away account on {@code roleId} with a random letters-only last name. The
     * caller registers its deletion ({@link AdminHttp#deleteUser}).
     */
    public static Disposable createDisposable(AdminHttp admin, int roleId) {
        String suffix = randomLetters(8);
        String email = "qa-disp-" + suffix + "@example.com";
        String password = newPassword();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("first_name", "Qadisposable");
        fields.put("last_name", "Qadisp" + suffix);
        fields.put("email", email);
        fields.put("password", password);
        fields.put("confirm", password);
        fields.put("designation", "QA Disposable");
        fields.put("phone", "+8801812345678");
        fields.put("role", Integer.toString(roleId));
        int id = admin.createUser(fields);
        log.info("Created disposable user {} (id {})", email, id);
        return new Disposable(id, new Credentials(email, password));
    }

    /** Role id of the "Read Reports" fixture role, created if missing (no user, no login). */
    public static int readReportsRoleId(AdminHttp admin) {
        return ROLE_IDS.computeIfAbsent(
                PREFIX + "report-read",
                name -> admin.ensureRole(name, List.of(PermissionCatalog.keyFor("Read Reports"))));
    }

    /**
     * A password matching both the server's complexity regexp and the create form's stricter
     * client-side one ({@code [A-Za-z\d@$!%*?&#.]}).
     */
    public static String newPassword() {
        return "Qa7!" + randomLetters(6) + RANDOM.nextInt(10_000) + "x";
    }

    /** Lower-case letters only (names in the user form must be letters). */
    public static String randomLetters(int length) {
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + RANDOM.nextInt(26)));
        }
        return sb.toString();
    }

    private static Path passwordsFile() {
        return StorageStateManager.stateDir().resolve(PASSWORDS_FILE);
    }

    private static Properties loadPasswords() {
        Properties p = new Properties();
        Path file = passwordsFile();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                p.load(r);
            } catch (IOException e) {
                log.warn("Cannot read {}: {}", file, e.toString());
            }
        }
        return p;
    }

    private static void savePasswords(Properties p) {
        Path file = passwordsFile();
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                p.store(
                        w,
                        "Fixture user passwords (git-ignored). Delete a line to recreate that user.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save fixture passwords to " + file, e);
        }
    }
}
