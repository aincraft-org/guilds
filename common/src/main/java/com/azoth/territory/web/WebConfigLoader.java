package com.azoth.territory.web;

import java.nio.file.Path;
import java.util.Map;

/**
 * Loads {@link WebConfig} from plugin configuration values.
 * Bukkit-free: callers pass the flattened config map (e.g. {@code FileConfiguration.getValues(true)}).
 */
public final class WebConfigLoader {
    private WebConfigLoader() {
    }

    public static WebConfig fromValues(Map<String, Object> cfg, Path dataFolder) {
        boolean enabled = bool(cfg, "web.enabled", false);
        String host = str(cfg, "web.bind", "127.0.0.1");
        int port = intOf(cfg, "web.port", 8765);
        String publicBase = str(cfg, "web.public-base-url", "");
        boolean trustProxy = bool(cfg, "web.trust-proxy", false);
        String token = str(cfg, "web.api-token", "");
        boolean cors = bool(cfg, "web.cors", false);
        String tileBase = str(cfg, "web.squaremap-tile-base-url", "");
        long sessionTtl = longOf(cfg, "web.session-ttl-seconds", WebConfig.DEFAULT_SESSION_TTL_SECONDS);

        if (enabled && token.isBlank()) {
            throw new IllegalArgumentException("web.api-token is required when web.enabled is true");
        }

        boolean tlsEnabled = bool(cfg, "web.tls.enabled", false);
        WebConfig.TlsSettings tls;
        if (tlsEnabled) {
            String ks = str(cfg, "web.tls.keystore", "keystore.p12");
            Path ksPath = Path.of(ks);
            if (!ksPath.isAbsolute()) {
                ksPath = dataFolder.resolve(ks);
            }
            String pass = str(cfg, "web.tls.password", "changeit");
            String keyPass = str(cfg, "web.tls.key-password", pass);
            String type = str(cfg, "web.tls.keystore-type", "PKCS12");
            tls = new WebConfig.TlsSettings(true, ksPath, pass, keyPass, type);
        } else {
            tls = WebConfig.TlsSettings.disabled();
        }

        return new WebConfig(
                enabled, host, port, publicBase, trustProxy, token, cors, tls, tileBase, sessionTtl);
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

    private static long longOf(Map<String, Object> cfg, String key, long def) {
        Object value = cfg.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }
}
