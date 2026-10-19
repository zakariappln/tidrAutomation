package com.ppln.automation.api.bdd.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.ppln.automation.api.client.MultipartField;
import com.ppln.automation.core.bdd.PlaceholderResolver;
import com.ppln.automation.core.utils.JsonUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Form fields for {@code Given I prepare a multipart request with the form fields}. The step
 * carries no table, so the fields live in {@code testdata/multipart/<Controller>.<Action>.json},
 * named after the request path ({@code /api/Document/Upload} → {@code Document.Upload.json}). The
 * file is one JSON object of field → value: scalars become text parts, arrays send the part once
 * per element, and {@code "@file:<name>"} attaches {@code testdata/files/<name>}. Values pass
 * through the scenario's placeholder resolver.
 */
public final class MultipartFixtures {

    static final String DIR = "testdata/multipart/";
    static final String FILES_DIR = "testdata/files/";
    private static final String FILE_PREFIX = "@file:";

    private MultipartFixtures() {}

    /** {@code /api/Document/Upload?x=1} → {@code Document.Upload.json}. */
    public static String fixtureName(String path) {
        String p =
                path.replaceFirst("[?#].*$", "").replaceFirst("^/?api/", "").replaceAll("/+$", "");
        return p.replace('/', '.') + ".json";
    }

    public static List<MultipartField> load(String path, PlaceholderResolver resolver) {
        String resource = DIR + fixtureName(path);
        JsonNode root = JsonUtils.readTree(resolver.resolve(read(resource, path)));
        if (!root.isObject()) {
            throw new IllegalStateException(
                    resource + " must be a JSON object of form field -> value");
        }
        var fields = new ArrayList<MultipartField>();
        for (var e : root.properties()) {
            JsonNode value = e.getValue();
            if (value.isArray()) {
                value.forEach(v -> fields.add(part(e.getKey(), v)));
            } else {
                fields.add(part(e.getKey(), value));
            }
        }
        return List.copyOf(fields);
    }

    private static MultipartField part(String name, JsonNode value) {
        String text = value.isTextual() ? value.asText() : value.toString();
        if (!text.startsWith(FILE_PREFIX)) {
            return MultipartField.text(name, text);
        }
        String fileName = text.substring(FILE_PREFIX.length()).trim();
        String mime = URLConnection.guessContentTypeFromName(fileName);
        return MultipartField.file(
                name,
                fileName,
                mime == null ? "application/octet-stream" : mime,
                bytes(FILES_DIR + fileName));
    }

    private static String read(String resource, String path) {
        try (InputStream in =
                MultipartFixtures.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(
                        "No multipart form fields for %s: create src/test/resources/%s (see testdata/multipart/README.md for the Swagger field list)"
                                .formatted(path, resource));
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
    }

    private static byte[] bytes(String resource) {
        try (InputStream in =
                MultipartFixtures.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Multipart file part not found on classpath: " + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
    }
}
