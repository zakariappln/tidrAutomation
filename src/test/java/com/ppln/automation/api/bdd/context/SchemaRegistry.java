package com.ppln.automation.api.bdd.context;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Loads each JSON Schema once per JVM. Schemas are immutable after load, so this is the one
 * intentionally static holder in the glue.
 */
public final class SchemaRegistry {

    private static final com.networknt.schema.SchemaRegistry FACTORY =
            com.networknt.schema.SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12);
    private static final Map<String, Schema> CACHE = new ConcurrentHashMap<>();

    private SchemaRegistry() {}

    /**
     * @return empty string when valid, else one violation per line
     */
    public static String validate(String classpathSchema, String json) {
        Schema schema = CACHE.computeIfAbsent(classpathSchema, SchemaRegistry::load);
        try {
            List<Error> errors = schema.validate(json, InputFormat.JSON);
            return errors.stream()
                    .map(Error::getMessage)
                    .sorted()
                    .collect(Collectors.joining(System.lineSeparator()));
        } catch (RuntimeException e) {
            return "response body could not be validated: " + e.getMessage();
        }
    }

    private static Schema load(String path) {
        try (InputStream in = SchemaRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Schema not found on classpath: " + path);
            }
            return FACTORY.getSchema(in, InputFormat.JSON);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read schema " + path, e);
        }
    }
}
