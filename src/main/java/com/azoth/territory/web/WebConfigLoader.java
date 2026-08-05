package com.azoth.territory.web;

import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;

/**
 * Loads {@link WebConfig} from the plugin {@code config.yml}.
 */
public final class WebConfigLoader {
    private WebConfigLoader() {
    }

    public static WebConfig fromBukkit(FileConfiguration cfg, Path dataFolder) {
        boolean enabled = cfg.getBoolean("web.enabled", true);
        String host = cfg.getString("web.bind", "0.0.0.0");
        int port = cfg.getInt("web.port", 8765);
        String publicBase = cfg.getString("web.public-base-url", "");
        boolean trustProxy = cfg.getBoolean("web.trust-proxy", true);
        String token = cfg.getString("web.api-token", "");
        boolean cors = cfg.getBoolean("web.cors", true);

        boolean tlsEnabled = cfg.getBoolean("web.tls.enabled", false);
        WebConfig.TlsSettings tls;
        if (tlsEnabled) {
            String ks = cfg.getString("web.tls.keystore", "keystore.p12");
            Path ksPath = Path.of(ks);
            if (!ksPath.isAbsolute()) {
                ksPath = dataFolder.resolve(ks);
            }
            String pass = cfg.getString("web.tls.password", "changeit");
            String keyPass = cfg.getString("web.tls.key-password", pass);
            String type = cfg.getString("web.tls.keystore-type", "PKCS12");
            tls = new WebConfig.TlsSettings(true, ksPath, pass, keyPass, type);
        } else {
            tls = WebConfig.TlsSettings.disabled();
        }

        return new WebConfig(enabled, host, port, publicBase, trustProxy, token, cors, tls);
    }
}
