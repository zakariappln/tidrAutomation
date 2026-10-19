package com.ppln.automation.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Resolves configuration for one <b>environment</b> ({@code staging | uat | prod}).
 *
 * <p>Files, later ones override earlier ones:
 *
 * <ol>
 *   <li>{@code config/default.properties} - every key with a safe default (browser, timeouts,
 *       artifacts)
 *   <li>{@code config/<env>.properties} - the environment's UI/API/auth hosts (required)
 * </ol>
 *
 * <p>Per key, the runtime precedence is: {@code -Dkey}, then env var {@code KEY} (dots to
 * underscores, upper-cased, e.g. {@code API_BASE_URL}), then the files above. The environment
 * itself comes from {@code -Denv} / {@code TEST_ENV} (the bare {@code ENV} variable is ignored
 * because POSIX shells use it).
 *
 * <p>Production is guarded: {@code env=prod} refuses to start unless {@code -Dprod.confirm=true}
 * (or {@code PROD_CONFIRM=true}) is present.
 */
public final class ConfigManager {

    public static final String DEFAULT_ROLE = "default";

    private static final Properties PROPS = loadProperties();
    private static final FrameworkConfig CONFIG = build(PROPS);

    private ConfigManager() {}

    public static FrameworkConfig config() {
        return CONFIG;
    }

    /** Any key, resolved with the standard precedence; empty when absent or blank. */
    public static Optional<String> optional(String key) {
        return resolve(key, PROPS);
    }

    /**
     * Credentials for a role. Looks up {@code auth.<role>.username} / {@code auth.<role>.password}
     * (env vars {@code AUTH_<ROLE>_USERNAME} / {@code AUTH_<ROLE>_PASSWORD}) and falls back to the
     * default pair {@code auth.username} / {@code auth.password} ({@code AUTH_USERNAME} / {@code
     * AUTH_PASSWORD}).
     */
    public static Credentials credentialsFor(String role) {
        String normalized =
                role == null || role.isBlank()
                        ? DEFAULT_ROLE
                        : role.trim().toLowerCase(Locale.ROOT);
        Optional<String> user = optional("auth." + normalized + ".username");
        Optional<String> pass = optional("auth." + normalized + ".password");
        if (user.isPresent() && pass.isPresent()) {
            return new Credentials(user.get(), pass.get());
        }
        String defaultUser =
                optional("auth.username")
                        .orElseThrow(() -> missingCredentials(role, "AUTH_USERNAME"));
        String defaultPass =
                optional("auth.password")
                        .orElseThrow(() -> missingCredentials(role, "AUTH_PASSWORD"));
        return new Credentials(defaultUser, defaultPass);
    }

    private static IllegalStateException missingCredentials(String role, String envVar) {
        return new IllegalStateException(
                "No credentials for role '%s': set %s (or auth.<role>.* keys) as environment variables"
                        .formatted(role, envVar));
    }

    private static Properties loadProperties() {
        var props = new Properties();
        loadInto(props, "config/default.properties", true);

        Environment environment = Environment.from(selector("env", "TEST_ENV", props));
        loadInto(props, "config/%s.properties".formatted(environment.key()), true);
        // Optional, git-ignored (*.local.properties): per-developer credentials and overrides.
        loadInto(props, "config/%s.local.properties".formatted(environment.key()), false);

        props.setProperty("env", environment.key());
        guardProduction(environment, props);
        return props;
    }

    /** {@code -Dkey} > env var > default.properties. Used only for the env selector. */
    private static String selector(String key, String envVar, Properties defaults) {
        return Optional.ofNullable(System.getProperty(key))
                .filter(v -> !v.isBlank())
                .or(() -> Optional.ofNullable(System.getenv(envVar)).filter(v -> !v.isBlank()))
                .or(() -> Optional.ofNullable(defaults.getProperty(key)).filter(v -> !v.isBlank()))
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "No %s selected: pass -D%s=... or set %s (no default in config/default.properties)"
                                                .formatted(key, key, envVar)));
    }

    private static void guardProduction(Environment environment, Properties props) {
        if (environment.isProduction()
                && !Boolean.parseBoolean(resolve("prod.confirm", props).orElse("false"))) {
            throw new IllegalStateException(
                    "Refusing to run against PROD without -Dprod.confirm=true (or PROD_CONFIRM=true). "
                            + "Only read-only 'sanity' checks belong there.");
        }
    }

    private static FrameworkConfig build(Properties props) {
        return new FrameworkConfig(
                Environment.from(props.getProperty("env")),
                get("base.url", props),
                get("api.base.url", props),
                get("browser", props),
                Boolean.parseBoolean(get("headless", props)),
                Integer.parseInt(get("slow.mo.ms", props)),
                Integer.parseInt(get("viewport.width", props)),
                Integer.parseInt(get("viewport.height", props)),
                get("locale", props),
                Integer.parseInt(get("timeout.action.ms", props)),
                Integer.parseInt(get("timeout.navigation.ms", props)),
                Integer.parseInt(get("timeout.expect.ms", props)),
                ArtifactMode.from(get("trace.mode", props)),
                ArtifactMode.from(get("video.mode", props)),
                ArtifactMode.from(get("screenshot.mode", props)),
                Integer.parseInt(get("retry.count", props)),
                Integer.parseInt(get("api.timeout.ms", props)),
                resolve("auth.base.url", props).orElse(""),
                get("auth.login.path", props),
                get("auth.token.json.pointer", props),
                Boolean.parseBoolean(resolve("soft.checks", props).orElse("false")));
    }

    private static String get(String key, Properties props) {
        return resolve(key, props)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Missing config key '%s' for env=%s"
                                                .formatted(key, props.getProperty("env"))));
    }

    private static Optional<String> resolve(String key, Properties props) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) {
            return Optional.of(sys);
        }
        String envValue = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        if (envValue != null && !envValue.isBlank()) {
            return Optional.of(envValue);
        }
        return Optional.ofNullable(props.getProperty(key)).filter(v -> !v.isBlank());
    }

    private static void loadInto(Properties props, String classpathFile, boolean required) {
        try (InputStream in =
                ConfigManager.class.getClassLoader().getResourceAsStream(classpathFile)) {
            if (in == null) {
                if (required) {
                    throw new IllegalStateException(
                            "Config file not found on classpath: "
                                    + classpathFile
                                    + " (create it under src/test/resources/config/)");
                }
                return;
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + classpathFile, e);
        }
    }
}
