package com.azoth.territory.persist;

import java.util.Objects;

/** Connection settings for the shared durable database. */
public final class DatabaseSettings {
    private final DatabaseType type;
    private final String host;
    private final int port;
    private final String name;
    private final String user;
    private final String password;
    private final boolean ssl;
    private final int poolSize;
    private final String jdbcUrlOverride;

    public DatabaseSettings(
            String host, int port, String name, String user, String password,
            boolean ssl, int poolSize, String jdbcUrlOverride) {
        this(DatabaseType.POSTGRESQL, host, port, name, user, password, ssl, poolSize, jdbcUrlOverride);
    }

    public DatabaseSettings(
            DatabaseType type, String host, int port, String name, String user, String password,
            boolean ssl, int poolSize, String jdbcUrlOverride) {
        this.type = Objects.requireNonNull(type, "type");
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.name = Objects.requireNonNull(name, "name");
        this.user = Objects.requireNonNull(user, "user");
        this.password = password == null ? "" : password;
        this.ssl = ssl;
        this.poolSize = poolSize;
        this.jdbcUrlOverride = jdbcUrlOverride == null ? "" : jdbcUrlOverride;
    }

    public static DatabaseSettings defaults() {
        return new DatabaseSettings(DatabaseType.POSTGRESQL, "127.0.0.1", 5432,
                "azoth_territory", "azoth", "", false, 10, "");
    }

    public DatabaseType type() { return type; }
    public String host() { return host; }
    public int port() { return port; }
    public String name() { return name; }
    public String user() { return user; }
    public String password() { return password; }
    public boolean ssl() { return ssl; }
    public int poolSize() { return poolSize; }

    public String jdbcUrl() {
        if (!jdbcUrlOverride.isBlank()) return jdbcUrlOverride;
        if (type == DatabaseType.MYSQL) {
            String url = "jdbc:mysql://" + host + ":" + port + "/" + name;
            return url + "?useSSL=" + ssl + "&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        }
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + name;
        return ssl ? url + "?sslmode=require" : url;
    }
}
