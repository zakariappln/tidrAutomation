package com.ppln.automation.ui.bdd.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * Minimal in-memory reader for a gzip-compressed POSIX/ustar tar (the app's diagnostics bundle).
 * Contents stay in memory and are never logged: the bundle carries system logs. Only regular files
 * are kept, keyed by their full path inside the archive.
 */
public final class TarGzArchive {

    private static final int BLOCK = 512;

    private final Map<String, byte[]> files;

    private TarGzArchive(Map<String, byte[]> files) {
        this.files = Collections.unmodifiableMap(files);
    }

    /**
     * Parses {@code bytes}; throws {@link IllegalArgumentException} if they are not a readable
     * tar.gz (bad gzip stream, truncated entry, or a header whose checksum does not match).
     */
    public static TarGzArchive read(byte[] bytes) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] header = new byte[BLOCK];
            while (readFully(in, header)) {
                if (isZeroBlock(header)) {
                    break; // end-of-archive marker
                }
                verifyChecksum(header);
                String name = text(header, 0, 100);
                String prefix = text(header, 345, 155);
                String path = prefix.isEmpty() ? name : prefix + "/" + name;
                String sizeField = text(header, 124, 12).trim();
                long size = sizeField.isEmpty() ? 0 : Long.parseLong(sizeField, 8);
                char type = (char) header[156];
                byte[] content = new byte[Math.toIntExact(size)];
                if (size > 0 && !readFully(in, content)) {
                    throw new IllegalArgumentException("Truncated entry " + path);
                }
                long padding = (BLOCK - size % BLOCK) % BLOCK;
                in.skipNBytes(padding);
                if (type == '0' || type == 0) {
                    files.put(path, content);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Not a readable tar.gz: " + e.getMessage(), e);
        }
        return new TarGzArchive(files);
    }

    /** Regular-file paths with their sizes, for the report (never the contents). */
    public String listing() {
        return files.entrySet().stream()
                .map(e -> e.getKey() + "  (" + e.getValue().length + " bytes)")
                .collect(Collectors.joining(System.lineSeparator()));
    }

    public Map<String, byte[]> files() {
        return files;
    }

    /** True if some file's base name is {@code baseName}. */
    public boolean containsFileNamed(String baseName) {
        return files.keySet().stream()
                .anyMatch(p -> p.equals(baseName) || p.endsWith("/" + baseName));
    }

    private static boolean readFully(InputStream in, byte[] buffer) throws IOException {
        int read = in.readNBytes(buffer, 0, buffer.length);
        if (read == 0) {
            return false;
        }
        if (read < buffer.length) {
            throw new IllegalArgumentException("Unexpected end of tar stream");
        }
        return true;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static void verifyChecksum(byte[] header) {
        long expected = Long.parseLong(text(header, 148, 8).trim(), 8);
        long sum = 0;
        for (int i = 0; i < BLOCK; i++) {
            sum += (i >= 148 && i < 156) ? ' ' : (header[i] & 0xff);
        }
        if (sum != expected) {
            throw new IllegalArgumentException("Tar header checksum mismatch");
        }
    }

    private static String text(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }
}
