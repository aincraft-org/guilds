package com.azoth.territory.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class HttpResponses {
    private HttpResponses() {
    }

    static void applyCors(HttpExchange exchange, WebConfig config) {
        if (!config.corsEnabled()) {
            return;
        }
        var h = exchange.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Api-Token");
        h.set("Access-Control-Max-Age", "86400");
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
        String esc = message == null ? "" : message.replace("\\", "\\\\").replace("\"", "\\\"");
        json(exchange, 400, "{\"error\":\"bad_request\",\"message\":\"" + esc + "\"}", config);
    }

    static void unauthorized(HttpExchange exchange, WebConfig config) throws IOException {
        json(exchange, 401, "{\"error\":\"unauthorized\"}", config);
    }

    static void methodNotAllowed(HttpExchange exchange, WebConfig config) throws IOException {
        json(exchange, 405, "{\"error\":\"method_not_allowed\"}", config);
    }

    static void serverError(HttpExchange exchange, String message, WebConfig config) throws IOException {
        String esc = message == null ? "internal" : message.replace("\\", "\\\\").replace("\"", "\\\"");
        json(exchange, 500, "{\"error\":\"internal\",\"message\":\"" + esc + "\"}", config);
    }

    static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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
