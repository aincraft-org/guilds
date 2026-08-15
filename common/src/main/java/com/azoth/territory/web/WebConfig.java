package com.azoth.territory.web;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for the embedded territory web submodule (HTTP/HTTPS + reverse proxy).
 */
public final class WebConfig {
    public static final long DEFAULT_SESSION_TTL_SECONDS = 28_800L;

    private final boolean enabled;
    private final String bindHost;
    private final int port;
    private final String publicBaseUrl;
    private final boolean trustProxy;
    private final String apiToken;
    private final boolean corsEnabled;
    private final TlsSettings tls;
    private final String squaremapTileBaseUrl;
    private final long sessionTtlSeconds;

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

    public WebConfig(
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
        this.enabled = enabled;
        this.bindHost = bindHost == null || bindHost.isBlank() ? "0.0.0.0" : bindHost.trim();
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("web port out of range: " + port);
        }
        this.port = port;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        this.trustProxy = trustProxy;
        this.apiToken = apiToken == null ? "" : apiToken;
        this.corsEnabled = corsEnabled;
        this.tls = tls == null ? TlsSettings.disabled() : tls;
        this.squaremapTileBaseUrl = squaremapTileBaseUrl == null ? "" : squaremapTileBaseUrl.trim()
                .replaceAll("/+$", "");
        if (sessionTtlSeconds < 1) {
            throw new IllegalArgumentException("sessionTtlSeconds must be positive");
        }
        this.sessionTtlSeconds = sessionTtlSeconds;
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

    public boolean enabled() {
        return enabled;
    }

    public String bindHost() {
        return bindHost;
    }

    public int port() {
        return port;
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    public boolean trustProxy() {
        return trustProxy;
    }

    public String apiToken() {
        return apiToken;
    }

    public boolean requiresAuth() {
        return apiToken != null && !apiToken.isBlank();
    }

    public boolean corsEnabled() {
        return corsEnabled;
    }

    public TlsSettings tls() {
        return tls;
    }

    public boolean https() {
        return tls.enabled();
    }

    /**
     * Base URL for squaremap (no trailing slash), e.g. {@code http://localhost:8080}.
     * Empty means the editor shows a chunk grid only.
     */
    public String squaremapTileBaseUrl() {
        return squaremapTileBaseUrl;
    }

    public long sessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    /**
     * TLS keystore settings. When disabled, the server serves plain HTTP
     * (suitable behind a TLS-terminating reverse proxy).
     */
    public static final class TlsSettings {
        private final boolean enabled;
        private final Path keystorePath;
        private final String keystorePassword;
        private final String keyPassword;
        private final String keystoreType;

        public TlsSettings(
                boolean enabled,
                Path keystorePath,
                String keystorePassword,
                String keyPassword,
                String keystoreType
        ) {
            this.enabled = enabled;
            this.keystorePath = keystorePath;
            this.keystorePassword = keystorePassword == null ? "" : keystorePassword;
            this.keyPassword = keyPassword == null ? this.keystorePassword : keyPassword;
            this.keystoreType = keystoreType == null || keystoreType.isBlank() ? "PKCS12" : keystoreType;
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

        public boolean enabled() {
            return enabled;
        }

        public Path keystorePath() {
            return keystorePath;
        }

        public String keystorePassword() {
            return keystorePassword;
        }

        public String keyPassword() {
            return keyPassword;
        }

        public String keystoreType() {
            return keystoreType;
        }
    }
}
