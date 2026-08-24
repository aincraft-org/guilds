package org.aincraft.guilds.territory.persist;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads SQL statements from classpath resources under {@code /sql/}.
 *
 * <p>{@code {schema}} placeholders are replaced with a schema identifier.
 * An empty schema strips {@code {schema}.} so unqualified table names remain
 * valid. Other identifier placeholders such as {@code {table}} may be supplied
 * in the replacements map.</p>
 */
public final class SqlStatements {
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private SqlStatements() {
    }

    public static String load(String name) {
        return render(read(name), "", Map.of());
    }

    public static String load(String name, String schema) {
        return render(read(name), schema, Map.of());
    }

    public static String load(String name, Map<String, String> placeholders) {
        Objects.requireNonNull(placeholders, "placeholders");
        String schema = placeholders.getOrDefault("schema", "");
        return render(read(name), schema, placeholders);
    }

    private static String render(String sql, String schema, Map<String, String> placeholders) {
        String result = applySchema(sql, schema);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if ("schema".equals(entry.getKey())) {
                continue;
            }
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    static String applySchema(String sql, String schema) {
        if (schema == null || schema.isBlank()) {
            return sql.replace("{schema}.", "").replace("{schema}", "");
        }
        return sql.replace("{schema}", schema);
    }

    private static String read(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.startsWith("/") || name.contains("..")) {
            throw new IllegalArgumentException("invalid SQL resource name: " + name);
        }
        return CACHE.computeIfAbsent(name, SqlStatements::readUncached);
    }

    private static String readUncached(String name) {
        String path = "/sql/" + name;
        try (InputStream in = SqlStatements.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing SQL resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load SQL resource: " + path, e);
        }
    }
}
