package org.aincraft.guilds.territory.web;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for the embedded territory web submodule (HTTP/HTTPS + reverse proxy).
 */
public record WebConfig(
        boolean enabled,
        String bindHost,
        int port,
        String publicBaseUrl,
        boolean trustProxy,
        String apiToken,
        boolean corsEnabled,
        TlsSettings tls,
        String squaremapTileBaseUrl,
        long sessionTtlSeconds
) {
    public static final long DEFAULT_SESSION_TTL_SECONDS = 28_800L;

    public WebConfig(
            boolean enabled,
            String bindHost,
            int port,
            String publicBaseUrl,
            boolean trustProxy,
            String apiToken,
            boolean corsEnabled,
            TlsSettings tls
    ) {
        this(enabled, bindHost, port, publicBaseUrl, trustProxy, apiToken, corsEnabled, tls, "",
                DEFAULT_SESSION_TTL_SECONDS);
    }

    public WebConfig {
        bindHost = bindHost == null || bindHost.isBlank() ? "0.0.0.0" : bindHost.trim();
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("web port out of range: " + port);
        }
        publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        apiToken = apiToken == null ? "" : apiToken;
        tls = tls == null ? TlsSettings.disabled() : tls;
        squaremapTileBaseUrl = squaremapTileBaseUrl == null ? "" : squaremapTileBaseUrl.trim()
                .replaceAll("/+$", "");
        if (sessionTtlSeconds < 1) {
            throw new IllegalArgumentException("sessionTtlSeconds must be positive");
        }
    }

    public static WebConfig defaults() {
        return new WebConfig(
                false,
                "127.0.0.1",
                8765,
                "",
                false,
                "",
                false,
                TlsSettings.disabled(),
                "",
                DEFAULT_SESSION_TTL_SECONDS
        );
    }

    public boolean requiresAuth() {
        return apiToken != null && !apiToken.isBlank();
    }

    public boolean https() {
        return tls.enabled();
    }

    /**
     * Base URL for squaremap (no trailing slash), e.g. {@code http://localhost:8080}.
     * Empty means the editor shows a chunk grid only.
     */
    @Override
    public String squaremapTileBaseUrl() {
        return squaremapTileBaseUrl;
    }

    /**
     * TLS keystore settings. When disabled, the server serves plain HTTP
     * (suitable behind a TLS-terminating reverse proxy).
     */
    public record TlsSettings(
            boolean enabled,
            Path keystorePath,
            String keystorePassword,
            String keyPassword,
            String keystoreType
    ) {
        public TlsSettings {
            keystorePassword = keystorePassword == null ? "" : keystorePassword;
            keyPassword = keyPassword == null ? keystorePassword : keyPassword;
            keystoreType = keystoreType == null || keystoreType.isBlank() ? "PKCS12" : keystoreType;
            if (enabled) {
                Objects.requireNonNull(keystorePath, "keystorePath required when TLS enabled");
            }
        }

        public static TlsSettings disabled() {
            return new TlsSettings(false, null, "", "", "PKCS12");
        }

        public static TlsSettings of(Path keystorePath, String password) {
            return new TlsSettings(true, keystorePath, password, password, "PKCS12");
        }
    }
}
