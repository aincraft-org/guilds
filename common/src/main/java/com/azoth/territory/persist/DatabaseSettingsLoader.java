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
        int poolSize = intOf(cfg, "database.pool-size", 10);
        String jdbcUrl = str(cfg, "database.jdbc-url", "");
        validateTransport(type, host, ssl, jdbcUrl);
        return new DatabaseSettings(type, host, port, name, user, password, ssl, poolSize, jdbcUrl);
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
            DatabaseType type, String configuredHost, boolean ssl, String jdbcUrl) {
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
        return normalized.equals("localhost")
                || normalized.equals("127.0.0.1")
                || normalized.equals("::1")
                || normalized.equals("[::1]");
    }
}
