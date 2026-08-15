package com.azoth.territory.persist;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@link DatabaseSettings} from plugin configuration values.
 * Bukkit-free: callers pass the flattened config map
 * (e.g. {@code FileConfiguration.getValues(true)}).
 */
public final class DatabaseSettingsLoader {
    private DatabaseSettingsLoader() {
    }

    public static DatabaseSettings fromValues(Map<String, Object> cfg) {
        DatabaseType type = DatabaseType.parse(str(cfg, "database.type", "postgresql"));
        int defaultPort = type == DatabaseType.MYSQL ? 3306 : 5432;
        String host = str(cfg, "database.host", "127.0.0.1");
        int port = intOf(cfg, "database.port", defaultPort);
        String name = str(cfg, "database.name", "azoth_territory");
        String user = str(cfg, "database.user", "azoth");
        String password = str(cfg, "database.password", "");
        boolean ssl = bool(cfg, "database.ssl", false);
        String sslMode = str(cfg, "database.ssl-mode", "").trim();
        String sslCaCert = str(cfg, "database.ssl-ca-cert", "").trim();
        String sslTrustStore = str(cfg, "database.ssl-trust-store", "").trim();
        String sslTrustStorePassword = str(cfg, "database.ssl-trust-store-password", "");
        String sslTrustStoreType = str(cfg, "database.ssl-trust-store-type", "PKCS12").trim().toUpperCase(Locale.ROOT);
        int poolSize = intOf(cfg, "database.pool-size", 10);
        String jdbcUrl = str(cfg, "database.jdbc-url", "");
        validateTransport(type, host, ssl, sslMode, sslCaCert, sslTrustStore, sslTrustStorePassword, sslTrustStoreType, jdbcUrl);
        String resolvedMode = resolveSslMode(type, host, ssl, sslMode);
        return new DatabaseSettings(type, host, port, name, user, password, ssl, resolvedMode,
                sslCaCert, sslTrustStore, sslTrustStorePassword, sslTrustStoreType, poolSize, jdbcUrl);
    }

    private static boolean bool(Map<String, Object> cfg, String key, boolean def) {
        Object value = cfg.get(key);
        return value instanceof Boolean b ? b : def;
    }

    private static String str(Map<String, Object> cfg, String key, String def) {
        Object value = cfg.get(key);
        return value != null ? value.toString() : def;
    }

    private static int intOf(Map<String, Object> cfg, String key, int def) {
        Object value = cfg.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private static void validateTransport(
            DatabaseType type, String configuredHost, boolean ssl, String sslMode,
            String sslCaCert, String sslTrustStore, String sslTrustStorePassword,
            String sslTrustStoreType, String jdbcUrl) {
        if (!jdbcUrl.isBlank()) {
            String targetHost = jdbcHost(jdbcUrl);
            if (targetHost == null) {
                throw new IllegalArgumentException("database.jdbc-url must be a valid JDBC URL");
            }
            if (isLoopbackHost(targetHost) || hasTlsParameter(type, jdbcUrl)) {
                return;
            }
            throw new IllegalArgumentException("database.ssl must be true for non-loopback database hosts");
        }
        if (!ssl && !isLoopbackHost(configuredHost)) {
            throw new IllegalArgumentException("database.ssl must be true for non-loopback database hosts");
        }
        if (!sslMode.isBlank()) {
            validateSslMode(type, ssl, configuredHost, sslMode);
        }
        if (type == DatabaseType.MYSQL && !sslCaCert.isBlank()) {
            throw new IllegalArgumentException(
                    "database.ssl-ca-cert is not supported for MySQL; use database.ssl-trust-store (JKS/PKCS12)");
        }
        if (type == DatabaseType.POSTGRESQL && !sslTrustStore.isBlank()) {
            throw new IllegalArgumentException(
                    "database.ssl-trust-store is not supported for PostgreSQL; use database.ssl-ca-cert (PEM)");
        }
        if (!sslTrustStore.isBlank()) {
            if (!"JKS".equals(sslTrustStoreType) && !"PKCS12".equals(sslTrustStoreType)) {
                throw new IllegalArgumentException(
                        "database.ssl-trust-store-type must be JKS or PKCS12");
            }
            if (sslTrustStorePassword.isBlank()) {
                throw new IllegalArgumentException(
                        "database.ssl-trust-store-password is required when database.ssl-trust-store is set");
            }
        }
    }

    private static String resolveSslMode(DatabaseType type, String host, boolean ssl, String sslMode) {
        if (!sslMode.isBlank()) {
            return normalizeSslMode(type, sslMode);
        }
        if (!ssl) {
            return type == DatabaseType.MYSQL ? "DISABLED" : "";
        }
        return isLoopbackHost(host)
                ? (type == DatabaseType.MYSQL ? "REQUIRED" : "require")
                : (type == DatabaseType.MYSQL ? "VERIFY_IDENTITY" : "verify-full");
    }

    private static void validateSslMode(DatabaseType type, boolean ssl, String host, String sslMode) {
        String mode = sslMode.toLowerCase(Locale.ROOT);
        if (type == DatabaseType.POSTGRESQL) {
            if (!"disable".equals(mode) && !"allow".equals(mode) && !"prefer".equals(mode)
                    && !"require".equals(mode) && !"verify-ca".equals(mode) && !"verify-full".equals(mode)) {
                throw new IllegalArgumentException("database.ssl-mode must be one of: disable, allow, prefer, require, verify-ca, verify-full");
            }
        } else {
            if (!"disabled".equals(mode) && !"preferred".equals(mode) && !"required".equals(mode)
                    && !"verify_ca".equals(mode) && !"verify_identity".equals(mode)) {
                throw new IllegalArgumentException("database.ssl-mode must be one of: DISABLED, PREFERRED, REQUIRED, VERIFY_CA, VERIFY_IDENTITY");
            }
        }
        if (!ssl && !isNoTlsMode(type, mode)) {
            throw new IllegalArgumentException("database.ssl=false conflicts with database.ssl-mode " + sslMode);
        }
        if (ssl && isNoTlsMode(type, mode)) {
            throw new IllegalArgumentException("database.ssl=true conflicts with database.ssl-mode " + sslMode);
        }
        if (ssl && !isLoopbackHost(host) && isFallbackMode(type, mode)) {
            throw new IllegalArgumentException(
                    "database.ssl-mode " + sslMode + " allows plaintext fallback and cannot be used for non-loopback hosts");
        }
    }

    private static boolean isNoTlsMode(DatabaseType type, String lowerMode) {
        if (type == DatabaseType.POSTGRESQL) {
            return "disable".equals(lowerMode);
        }
        return "disabled".equals(lowerMode);
    }

    private static boolean isFallbackMode(DatabaseType type, String lowerMode) {
        if (type == DatabaseType.POSTGRESQL) {
            return "allow".equals(lowerMode) || "prefer".equals(lowerMode);
        }
        return "preferred".equals(lowerMode);
    }

    private static String normalizeSslMode(DatabaseType type, String sslMode) {
        if (type == DatabaseType.POSTGRESQL) {
            return sslMode.toLowerCase(Locale.ROOT);
        }
        // MySQL modes are uppercase with underscores in the JDBC driver.
        return sslMode.toUpperCase(Locale.ROOT).replace("-", "_");
    }

    private static String jdbcHost(String jdbcUrl) {
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            return uri.getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean hasTlsParameter(DatabaseType type, String jdbcUrl) {
        URI uri;
        try {
            uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException e) {
            return false;
        }
        String query = uri.getRawQuery();
        if (query == null) {
            return false;
        }
        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String key = parameter.substring(0, separator).toLowerCase(Locale.ROOT);
            String value = parameter.substring(separator + 1).toLowerCase(Locale.ROOT);
            if (type == DatabaseType.MYSQL
                    && (key.equals("sslmode") || key.equals("usessl") || key.equals("requiressl"))
                    && (value.equals("required") || value.equals("verify_ca")
                    || value.equals("verify_identity") || value.equals("true"))) {
                return true;
            }
            if (type == DatabaseType.POSTGRESQL
                    && key.equals("sslmode")
                    && (value.equals("require") || value.equals("verify-ca")
                    || value.equals("verify-full"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoopbackHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("localhost") || normalized.equals("::1") || normalized.equals("[::1]")
                || normalized.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        if (normalized.startsWith("127.")) {
            String[] parts = normalized.split("\\.");
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
