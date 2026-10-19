package com.ppln.automation.ui.bdd.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Upload fixtures named in features ("valid.png", "notes.txt", "over-7mb.png", ...), generated on
 * demand under {@code target/upload-files/} so no binaries (and no 7 MB file) are committed. The
 * name decides the content: real minimal PNG/JPEG/GIF images, plain text, or an oversize PNG just
 * above the app's 7 MiB request limit.
 */
public final class UploadFiles {

    private static final Path DIR = Paths.get("target", "upload-files");
    private static final long OVER_LIMIT_BYTES = 7L * 1024 * 1024 + 512 * 1024;

    // 1x1 images
    private static final String PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    private static final String GIF = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
    private static final String JPG =
            "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=";

    private UploadFiles() {}

    public static synchronized Path get(String name) {
        Path file = DIR.resolve(name);
        try {
            Files.createDirectories(DIR);
            if (!Files.exists(file)) {
                Files.write(file, content(name));
            }
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create upload fixture " + file, e);
        }
    }

    private static byte[] content(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("over-7mb")) {
            byte[] png = Base64.getDecoder().decode(PNG);
            byte[] big = new byte[(int) OVER_LIMIT_BYTES];
            System.arraycopy(png, 0, big, 0, png.length);
            return big;
        }
        if (lower.endsWith(".png")) {
            return Base64.getDecoder().decode(PNG);
        }
        if (lower.endsWith(".gif")) {
            return Base64.getDecoder().decode(GIF);
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return Base64.getDecoder().decode(JPG);
        }
        return ("QA upload fixture " + name + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8);
    }
}
