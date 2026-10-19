package com.ppln.automation.api.client;

/**
 * One {@code multipart/form-data} part: a text value, or a file when {@code content} is non-null.
 * Repeating a name sends the part several times (array fields such as {@code Files}).
 */
public record MultipartField(
        String name, String value, String fileName, String mimeType, byte[] content) {

    public static MultipartField text(String name, String value) {
        return new MultipartField(name, value, null, null, null);
    }

    public static MultipartField file(
            String name, String fileName, String mimeType, byte[] content) {
        return new MultipartField(name, null, fileName, mimeType, content);
    }

    public boolean isFile() {
        return content != null;
    }

    /** Report-safe description: file parts show name and size, never content. */
    public String describe() {
        return isFile()
                ? "%s = <file %s, %s, %d bytes>".formatted(name, fileName, mimeType, content.length)
                : "%s = %s".formatted(name, value);
    }
}
