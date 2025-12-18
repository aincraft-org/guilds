package org.aincraft.database;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Database configuration holder.
 */
public class DatabaseConfig {
    private final DatabaseType type;
    private final String filePath;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int maxPoolSize;
    private final int minIdle;
    private final long connectionTimeout;
    private final long idleTimeout;
    private final long maxLifetime;

    private DatabaseConfig(Builder builder) {
        this.type = builder.type;
        this.filePath = builder.filePath;
        this.host = builder.host;
        this.port = builder.port;
        this.database = builder.database;
        this.username = builder.username;
        this.password = builder.password;
        this.maxPoolSize = builder.maxPoolSize;
        this.minIdle = builder.minIdle;
        this.connectionTimeout = builder.connectionTimeout;
        this.idleTimeout = builder.idleTimeout;
        this.maxLifetime = builder.maxLifetime;
    }

    public static DatabaseConfig fromConfig(ConfigurationSection section) {
        String typeStr = section.getString("type", "sqlite");
        DatabaseType type = DatabaseType.fromString(typeStr);

        Builder builder = new Builder(type);

        if (type.isFileBased()) {
            builder.filePath(section.getString("file-path", "guilds.db"));
        } else {
            builder.host(section.getString("host", "localhost"))
                   .port(section.getInt("port", getDefaultPort(type)))
                   .database(section.getString("database", "guilds"))
                   .username(section.getString("username", ""))
                   .password(section.getString("password", ""));
        }

        ConfigurationSection pool = section.getConfigurationSection("pool");
        if (pool != null) {
            builder.maxPoolSize(pool.getInt("maximum-pool-size", 10))
                   .minIdle(pool.getInt("minimum-idle", 2))
                   .connectionTimeout(pool.getLong("connection-timeout", 30000))
                   .idleTimeout(pool.getLong("idle-timeout", 600000))
                   .maxLifetime(pool.getLong("max-lifetime", 1800000));
        }

        return builder.build();
    }

    private static int getDefaultPort(DatabaseType type) {
        return switch (type) {
            case MYSQL, MARIADB -> 3306;
            case POSTGRESQL -> 5432;
            default -> 0;
        };
    }

    public String buildJdbcUrl() {
        return switch (type) {
            case SQLITE -> type.getJdbcPrefix() + filePath;
            case H2 -> type.getJdbcPrefix() + filePath + ";MODE=MySQL";
            case MYSQL, MARIADB -> type.getJdbcPrefix() + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            case POSTGRESQL -> type.getJdbcPrefix() + host + ":" + port + "/" + database;
        };
    }

    public DatabaseType getType() {
        return type;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public long getMaxLifetime() {
        return maxLifetime;
    }

    public static class Builder {
        private final DatabaseType type;
        private String filePath = "guilds.db";
        private String host = "localhost";
        private int port = 3306;
        private String database = "guilds";
        private String username = "";
        private String password = "";
        private int maxPoolSize = 10;
        private int minIdle = 2;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;

        public Builder(DatabaseType type) {
            this.type = type;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder database(String database) {
            this.database = database;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        public Builder minIdle(int minIdle) {
            this.minIdle = minIdle;
            return this;
        }

        public Builder connectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder idleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        public Builder maxLifetime(long maxLifetime) {
            this.maxLifetime = maxLifetime;
            return this;
        }

        public DatabaseConfig build() {
            return new DatabaseConfig(this);
        }
    }
}
