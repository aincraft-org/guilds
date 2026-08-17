package com.azoth.territory.persist;

import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/** Connection settings for the shared durable database. */
public record DatabaseSettings(
        DatabaseType type,
        String host,
        int port,
        String name,
        String user,
        String password,
        boolean ssl,
        String sslMode,
        String sslCaCert,
        String sslTrustStore,
        String sslTrustStorePassword,
        String sslTrustStoreType,
        int poolSize,
        String jdbcUrlOverride
) {

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

    public DatabaseSettings {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(user, "user");
        password = password == null ? "" : password;
        sslMode = sslMode == null ? "" : sslMode;
        sslCaCert = sslCaCert == null ? "" : sslCaCert;
        sslTrustStore = sslTrustStore == null ? "" : sslTrustStore;
        sslTrustStorePassword = sslTrustStorePassword == null ? "" : sslTrustStorePassword;
        sslTrustStoreType = (sslTrustStoreType == null || sslTrustStoreType.isBlank()) ? "PKCS12" : sslTrustStoreType;
        jdbcUrlOverride = jdbcUrlOverride == null ? "" : jdbcUrlOverride;
    }

    public static DatabaseSettings defaults() {
        return new DatabaseSettings(DatabaseType.POSTGRESQL, "127.0.0.1", 5432,
                "azoth_territory", "azoth", "", false, 10, "");
    }

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
