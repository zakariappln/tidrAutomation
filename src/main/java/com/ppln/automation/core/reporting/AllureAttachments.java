package com.ppln.automation.core.reporting;

import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;
import com.ppln.automation.core.utils.Secrets;
import io.qameta.allure.Allure;
import io.qameta.allure.AttachmentOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Attachment helpers used by UI and API layers. Text and JSON attachments are always passed through
 * {@link Secrets#mask(String)} so tokens and passwords never reach the report.
 */
public final class AllureAttachments {

    private static final ThreadLocal<List<String>> CONSOLE =
            ThreadLocal.withInitial(CopyOnWriteArrayList::new);

    private AllureAttachments() {}

    public static void screenshot(Page page, String name) {
        byte[] png = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
        Allure.attachment(
                name,
                "image/png",
                new ByteArrayInputStream(png),
                AttachmentOptions.withFileExtension(".png"));
    }

    public static void trace(Path zip) {
        file(
                "Playwright trace (open at https://trace.playwright.dev)",
                zip,
                "application/zip",
                ".zip");
    }

    public static void video(Path webm) {
        file("Video", webm, "video/webm", ".webm");
    }

    public static void text(String name, String content) {
        Allure.attachment(
                name,
                "text/plain",
                Secrets.mask(content),
                AttachmentOptions.withFileExtension(".txt"));
    }

    /**
     * A replayable shell command. Secret body fields are masked; the bearer token is not in the
     * text at all (see {@code CurlCommand}), so header masking is not applied and the command stays
     * runnable.
     */
    public static void curl(String name, String command) {
        Allure.attachment(
                name,
                "text/plain",
                Secrets.maskFields(command),
                AttachmentOptions.withFileExtension(".sh"));
    }

    public static void json(String name, String content) {
        Allure.attachment(
                name,
                "application/json",
                Secrets.mask(content),
                AttachmentOptions.withFileExtension(".json"));
    }

    /** Register with {@code page.onConsoleMessage(AllureAttachments::collectConsole)}. */
    public static void collectConsole(ConsoleMessage message) {
        CONSOLE.get().add("[%s] %s".formatted(message.type(), message.text()));
    }

    /** Attaches and clears the console buffer collected on the current thread. */
    public static void consoleLogs() {
        List<String> logs = CONSOLE.get();
        if (!logs.isEmpty()) {
            text("Browser console", String.join(System.lineSeparator(), logs));
        }
        logs.clear();
    }

    private static void file(String name, Path path, String mime, String extension) {
        try {
            Allure.attachment(
                    name,
                    mime,
                    Files.newInputStream(path),
                    AttachmentOptions.withFileExtension(extension));
        } catch (IOException e) {
            text(name + " (unavailable)", e.toString());
        }
    }
}
