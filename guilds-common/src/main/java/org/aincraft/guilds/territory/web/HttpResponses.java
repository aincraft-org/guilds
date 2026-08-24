package org.aincraft.guilds.territory.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class HttpResponses {
    private HttpResponses() {
    }

    private static final int MAX_BODY_BYTES = 1_048_576;

    static void applyCors(HttpExchange exchange, WebConfig config) {
        if (!config.corsEnabled()) {
            return;
        }
        String publicBase = config.publicBaseUrl();
        if (publicBase.isBlank()) {
            return;
        }
        String configuredOrigin;
        try {
            java.net.URI uri = new java.net.URI(publicBase);
            configuredOrigin = uri.getScheme() + "://" + uri.getAuthority();
        } catch (java.net.URISyntaxException e) {
            return;
        }

        String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
        if (requestOrigin == null || !requestOrigin.equalsIgnoreCase(configuredOrigin)) {
            return;
        }

        var h = exchange.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", requestOrigin);
        h.set("Vary", "Origin");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Api-Token");
        h.set("Access-Control-Max-Age", "86400");
    }

    /**
     * Reads a single cookie value from the request {@code Cookie} header.
     */
    static Optional<String> cookie(HttpExchange exchange, String name) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null || cookies.isEmpty()) {
            return Optional.empty();
        }
        String prefix = name + "=";
        for (String header : cookies) {
            if (header == null || header.isBlank()) {
                continue;
            }
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith(prefix)) {
                    return Optional.of(trimmed.substring(prefix.length()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Appends a Set-Cookie header. Does not send the response.
     */
    static void setCookie(
            HttpExchange exchange,
            String name,
            String value,
            long maxAgeSeconds,
            boolean secure
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(value == null ? "" : value);
        sb.append("; Path=/; HttpOnly; SameSite=Strict");
        if (maxAgeSeconds >= 0) {
            sb.append("; Max-Age=").append(maxAgeSeconds);
        }
        if (secure) {
            sb.append("; Secure");
        }
        exchange.getResponseHeaders().add("Set-Cookie", sb.toString());
    }

    static void clearCookie(HttpExchange exchange, String name, boolean secure) {
        setCookie(exchange, name, "", 0, secure);
    }

    static void json(HttpExchange exchange, int status, String body, WebConfig config) throws IOException {
        applyCors(exchange, config);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void text(HttpExchange exchange, int status, String contentType, String body, WebConfig config)
            throws IOException {
        applyCors(exchange, config);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void bytes(HttpExchange exchange, int status, String contentType, byte[] bytes, WebConfig config)
            throws IOException {
        applyCors(exchange, config);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void noContent(HttpExchange exchange, WebConfig config) throws IOException {
        applyCors(exchange, config);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    static void notFound(HttpExchange exchange, WebConfig config) throws IOException {
        json(exchange, 404, "{\"error\":\"not_found\"}", config);
    }

    static void badRequest(HttpExchange exchange, String message, WebConfig config) throws IOException {
        json(exchange, 400, "{\"error\":\"bad_request\"}", config);
    }

    static void unauthorized(HttpExchange exchange, WebConfig config) throws IOException {
        json(exchange, 401, "{\"error\":\"unauthorized\"}", config);
    }

    static void methodNotAllowed(HttpExchange exchange, WebConfig config) throws IOException {
        json(exchange, 405, "{\"error\":\"method_not_allowed\"}", config);
    }

    static void serverError(HttpExchange exchange, String message, WebConfig config) throws IOException {
        json(exchange, 500, "{\"error\":\"internal\"}", config);
    }
    static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("Request body exceeds maximum allowed size");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    static Map<String, String> queryParams(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                map.put(urlDecode(part), "");
            } else {
                map.put(urlDecode(part.substring(0, eq)), urlDecode(part.substring(eq + 1)));
            }
        }
        return map;
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
