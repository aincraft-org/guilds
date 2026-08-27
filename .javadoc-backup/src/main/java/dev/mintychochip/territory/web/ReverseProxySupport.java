package dev.mintychochip.territory.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Reverse-proxy awareness: reconstruct client scheme/host/base URL from
 * {@code X-Forwarded-*} headers when trust-proxy is enabled.
 * <p>
 * Typical deployment: nginx/Caddy terminates TLS and proxies to the embedded
 * plain HTTP port; the API still sees the public HTTPS origin.
 */
public final class ReverseProxySupport {
    private final WebConfig config;

    public ReverseProxySupport(WebConfig config) {
        this.config = config;
    }

    public String scheme(HttpExchange exchange) {
        if (config.trustProxy()) {
            String proto = firstHeader(exchange.getRequestHeaders(), "X-Forwarded-Proto");
            if (proto != null && !proto.isBlank()) {
                // may be "https,http" — take first
                return proto.split(",")[0].trim().toLowerCase(Locale.ROOT);
            }
        }
        if (!config.publicBaseUrl().isEmpty()) {
            try {
                String s = URI.create(config.publicBaseUrl()).getScheme();
                if (s != null) {
                    return s;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return config.https() ? "https" : "http";
    }

    public String host(HttpExchange exchange) {
        if (config.trustProxy()) {
            String forwarded = firstHeader(exchange.getRequestHeaders(), "X-Forwarded-Host");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        if (!config.publicBaseUrl().isEmpty()) {
            try {
                URI u = URI.create(config.publicBaseUrl());
                if (u.getHost() != null) {
                    if (u.getPort() > 0) {
                        return u.getHost() + ":" + u.getPort();
                    }
                    return u.getHost();
                }
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        String host = firstHeader(exchange.getRequestHeaders(), "Host");
        if (host != null && !host.isBlank()) {
            return host.trim();
        }
        return "localhost:" + config.port();
    }

    /**
     * Public origin (scheme://host[:port]) as seen by clients / reverse proxy.
     */
    public String publicOrigin(HttpExchange exchange) {
        if (!config.publicBaseUrl().isEmpty()) {
            String base = config.publicBaseUrl();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return base;
        }
        return scheme(exchange) + "://" + host(exchange);
    }

    public Optional<String> clientIp(HttpExchange exchange) {
        if (config.trustProxy()) {
            String xff = firstHeader(exchange.getRequestHeaders(), "X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return Optional.of(xff.split(",")[0].trim());
            }
            String realIp = firstHeader(exchange.getRequestHeaders(), "X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return Optional.of(realIp.trim());
            }
        }
        if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
            return Optional.of(exchange.getRemoteAddress().getAddress().getHostAddress());
        }
        return Optional.empty();
    }

    /**
     * Whether the effective client connection is HTTPS (direct TLS or forwarded proto).
     */
    public boolean isSecure(HttpExchange exchange) {
        return "https".equalsIgnoreCase(scheme(exchange));
    }

    static String firstHeader(Headers headers, String name) {
        if (headers == null) {
            return null;
        }
        // Headers is case-insensitive for get
        return headers.getFirst(name);
    }
}
