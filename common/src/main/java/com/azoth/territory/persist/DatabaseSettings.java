package com.azoth.territory.persist;

import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/** Connection settings for the shared durable database. */
public final class DatabaseSettings {
    private final DatabaseType type;
    private final String host;
    private final int port;
    private final String name;
    private final String user;
    private final String password;
    private final boolean ssl;
    private final String sslMode;
    private final String sslCaCert;
    private final String sslTrustStore;
    private final String sslTrustStorePassword;
    private final String sslTrustStoreType;
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
        this(type, host, port, name, user, password, ssl, "", "", "", "", "", poolSize, jdbcUrlOverride);
    }

    public DatabaseSettings(
            DatabaseType type, String host, int port, String name, String user, String password,
            boolean ssl, String sslMode, String sslCaCert, String sslTrustStore,
            String sslTrustStorePassword, String sslTrustStoreType,
            int poolSize, String jdbcUrlOverride) {
        this.type = Objects.requireNonNull(type, "type");
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.name = Objects.requireNonNull(name, "name");
        this.user = Objects.requireNonNull(user, "user");
        this.password = password == null ? "" : password;
        this.ssl = ssl;
        this.sslMode = sslMode == null ? "" : sslMode;
        this.sslCaCert = sslCaCert == null ? "" : sslCaCert;
        this.sslTrustStore = sslTrustStore == null ? "" : sslTrustStore;
        this.sslTrustStorePassword = sslTrustStorePassword == null ? "" : sslTrustStorePassword;
        this.sslTrustStoreType = (sslTrustStoreType == null || sslTrustStoreType.isBlank()) ? "PKCS12" : sslTrustStoreType;
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
    public String sslMode() { return sslMode; }
    public String sslCaCert() { return sslCaCert; }
    public String sslTrustStore() { return sslTrustStore; }
    public String sslTrustStorePassword() { return sslTrustStorePassword; }
    public String sslTrustStoreType() { return sslTrustStoreType; }
    public int poolSize() { return poolSize; }

    /** Base JDBC URL without driver properties. */
    public String jdbcUrl() {
        if (!jdbcUrlOverride.isBlank()) return jdbcUrlOverride;
        return "jdbc:" + (type == DatabaseType.MYSQL ? "mysql" : "postgresql") + "://"
                + host + ":" + port + "/" + name;
    }

    /**
     * Driver properties passed to HikariCP. These are kept out of the URL so that
     * trust-store passwords and other secrets are less likely to appear in logs.
     */
    public Properties dataSourceProperties() {
        Properties p = new Properties();
        if (!jdbcUrlOverride.isBlank()) {
            return p;
        }
        if (type == DatabaseType.MYSQL) {
            p.setProperty("serverTimezone", "UTC");
            String mode = effectiveMySqlMode();
            p.setProperty("sslMode", mode);
            p.setProperty("allowPublicKeyRetrieval", "DISABLED".equals(mode) ? "true" : "false");
            if (!sslTrustStore.isBlank() && !sslTrustStorePassword.isBlank()) {
                p.setProperty("trustCertificateKeyStoreUrl", "file:" + sslTrustStore);
                p.setProperty("trustCertificateKeyStoreType", sslTrustStoreType);
                p.setProperty("trustCertificateKeyStorePassword", sslTrustStorePassword);
            }
        } else {
            String mode = effectivePostgresMode();
            if (!mode.isBlank()) {
                p.setProperty("sslmode", mode);
            }
            if (!sslCaCert.isBlank()) {
                p.setProperty("sslrootcert", sslCaCert);
            }
        }
        return p;
    }

    private String effectivePostgresMode() {
        if (!sslMode.isBlank()) return sslMode;
        if (!ssl) return "";
        return isLoopbackHost() ? "require" : "verify-full";
    }

    private String effectiveMySqlMode() {
        if (!sslMode.isBlank()) return sslMode;
        if (!ssl) return "DISABLED";
        return isLoopbackHost() ? "REQUIRED" : "VERIFY_IDENTITY";
    }

    private boolean isLoopbackHost() {
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.equals("::1") || h.equals("[::1]")
                || h.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        if (h.startsWith("127.")) {
            String[] parts = h.split("\\.");
            if (parts.length == 4) {
                try {
                    for (String part : parts) {
                        int n = Integer.parseInt(part);
                        if (n < 0 || n > 255) return false;
                    }
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return false;
    }
}
