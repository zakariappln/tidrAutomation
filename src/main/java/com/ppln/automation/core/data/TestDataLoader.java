package com.ppln.automation.core.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ppln.automation.core.utils.JsonUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads JSON fixtures from {@code src/test/resources/testdata/**} into records. */
public final class TestDataLoader {

    private TestDataLoader() {}

    public static <T> T load(String classpathFile, Class<T> type) {
        return JsonUtils.fromJson(read(classpathFile), type);
    }

    public static <T> T load(String classpathFile, TypeReference<T> type) {
        return JsonUtils.fromJson(read(classpathFile), type);
    }

    public static String read(String classpathFile) {
        try (InputStream in =
                TestDataLoader.class.getClassLoader().getResourceAsStream(classpathFile)) {
            if (in == null) {
                throw new IllegalArgumentException(
                        "Test data file not found on classpath: " + classpathFile);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + classpathFile, e);
        }
    }
}
