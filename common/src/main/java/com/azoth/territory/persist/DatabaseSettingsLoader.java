package com.azoth.territory.persist;

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
        String host = str(cfg, "database.host", "127.0.0.1");
        int port = intOf(cfg, "database.port", 5432);
        String name = str(cfg, "database.name", "azoth_territory");
        String user = str(cfg, "database.user", "azoth");
        String password = str(cfg, "database.password", "");
        boolean ssl = bool(cfg, "database.ssl", false);
        int poolSize = intOf(cfg, "database.pool-size", 10);
        String jdbcUrl = str(cfg, "database.jdbc-url", "");
        return new DatabaseSettings(host, port, name, user, password, ssl, poolSize, jdbcUrl);
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
}
