package dev.mintychochip.sql;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads classpath {@code .sql} resources and prepares them with named parameters.
 */
public final class NamedSql {

    private final ClassLoader classLoader;
    private final String resourceRoot;
    private final ConcurrentHashMap<String, String> sources = new ConcurrentHashMap<>();

    private NamedSql(ClassLoader classLoader, String resourceRoot) {
        this.classLoader = classLoader;
        this.resourceRoot = resourceRoot.endsWith("/") ? resourceRoot : resourceRoot + "/";
    }

    public static NamedSql load(Class<?> anchor, String resourceRoot) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(resourceRoot, "resourceRoot");
        return new NamedSql(anchor.getClassLoader(), resourceRoot);
    }

    public static NamedSql guilds() {
        return load(NamedSql.class, "dev/mintychochip/guilds/sql");
    }

    public static NamedSql territory() {
        return load(NamedSql.class, "dev/mintychochip/territory/sql");
    }

    public ParsedSql sql(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        String source = sources.computeIfAbsent(relativePath, this::readResource);
        return ParsedSql.parse(source);
    }

    public String jdbc(String relativePath) {
        return sql(relativePath).jdbcSql(Map.of());
    }

    public String jdbc(String relativePath, Map<String, ?> params) {
        return sql(relativePath).jdbcSql(params);
    }

    public PreparedStatement prepare(Connection connection, String relativePath, Map<String, ?> params)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        PreparedStatement statement = connection.prepareStatement(sql(relativePath).jdbcSql(params));
        sql(relativePath).bind(statement, params);
        return statement;
    }

    private String readResource(String relativePath) {
        String path = resourceRoot + relativePath;
        try (InputStream input = classLoader.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("SQL resource not found: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read SQL resource: " + path, e);
        }
    }
}
