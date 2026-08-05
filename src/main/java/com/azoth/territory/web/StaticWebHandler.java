package com.azoth.territory.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

/**
 * Serves the embedded map UI from classpath {@code /web/} static assets.
 */
public final class StaticWebHandler implements HttpHandler {
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "json", "application/json; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon",
            "map", "application/json"
    );

    private final WebConfig config;
    private final ClassLoader classLoader;
    private final String resourceRoot;

    public StaticWebHandler(WebConfig config) {
        this(config, StaticWebHandler.class.getClassLoader(), "web");
    }

    public StaticWebHandler(WebConfig config, ClassLoader classLoader, String resourceRoot) {
        this.config = config;
        this.classLoader = classLoader;
        this.resourceRoot = resourceRoot.endsWith("/")
                ? resourceRoot.substring(0, resourceRoot.length() - 1)
                : resourceRoot;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpResponses.applyCors(exchange, config);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
                && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpResponses.methodNotAllowed(exchange, config);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            path = "/index.html";
        }
        // Prevent path traversal
        if (path.contains("..")) {
            HttpResponses.notFound(exchange, config);
            return;
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isEmpty()) {
            path = "index.html";
        }

        String resource = resourceRoot + "/" + path;
        try (InputStream in = classLoader.getResourceAsStream(resource)) {
            if (in == null) {
                // SPA-ish fallback: unknown paths → index.html for the map UI
                if (!path.contains(".")) {
                    try (InputStream index = classLoader.getResourceAsStream(resourceRoot + "/index.html")) {
                        if (index != null) {
                            byte[] bytes = index.readAllBytes();
                            HttpResponses.bytes(exchange, 200, "text/html; charset=utf-8", bytes, config);
                            return;
                        }
                    }
                }
                HttpResponses.notFound(exchange, config);
                return;
            }
            byte[] bytes = in.readAllBytes();
            String ext = extension(path);
            String ct = CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpResponses.applyCors(exchange, config);
                exchange.getResponseHeaders().set("Content-Type", ct);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.close();
                return;
            }
            HttpResponses.bytes(exchange, 200, ct, bytes, config);
        }
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "";
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
