package com.ppln.automation.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

@Epic("Framework")
@Feature("Environment configuration")
public class ConfigManagerTest {

    @Test(
            groups = {"unit"},
            description =
                    "Selected env is exposed and config/<env>.properties has been layered over defaults")
    @Story("Layering")
    @Severity(SeverityLevel.CRITICAL)
    public void shouldLayerEnvFileOverDefaults() {
        FrameworkConfig cfg = ConfigManager.config();
        assertThat(cfg.env()).isIn((Object[]) Environment.values());
        assertThat(cfg.apiBaseUrl())
                .as(
                        "config/%s.properties overrides the default.properties placeholder",
                        cfg.env().key())
                .doesNotContain("REPLACE-ME-api.invalid");
        assertThat(cfg.effectiveAuthBaseUrl()).isNotBlank();
    }

    @Test(
            groups = {"unit"},
            description = "-D system properties win over files; blank values count as absent")
    @Story("Precedence")
    @Severity(SeverityLevel.CRITICAL)
    public void shouldPreferSystemPropertyAndIgnoreBlanks() {
        System.setProperty("config.test.key", "from-system-property");
        try {
            assertThat(ConfigManager.optional("config.test.key")).contains("from-system-property");
        } finally {
            System.clearProperty("config.test.key");
        }
        assertThat(ConfigManager.optional("config.test.key")).isEmpty();
        assertThat(ConfigManager.optional("auth.expired.token"))
                .as("blank file value is absent")
                .isEmpty();
    }
}
