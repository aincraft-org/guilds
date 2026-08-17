package dev.mintychochip.territory.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Objects;

/**
 * Serves classpath static files under a URL prefix (e.g. {@code /editor/}).
 */
public final class StaticFileHandler implements HttpHandler {
    private final String urlPrefix;
    private final String classpathRoot;
    private final WebConfig config;
    private final String defaultFile;

    /**
     * @param urlPrefix     e.g. {@code /editor} (no trailing slash required)
     * @param classpathRoot e.g. {@code dev/mintychochip/territory/web/static/editor}
     */
    public StaticFileHandler(String urlPrefix, String classpathRoot, WebConfig config) {
        String p = Objects.requireNonNull(urlPrefix, "urlPrefix").trim();
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        this.urlPrefix = p;
        String root = Objects.requireNonNull(classpathRoot, "classpathRoot").trim();
        while (root.startsWith("/")) {
            root = root.substring(1);
        }
        if (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        this.classpathRoot = root;
        this.config = Objects.requireNonNull(config, "config");
        this.defaultFile = "index.html";
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if ("OPTIONS".equals(method)) {
            HttpResponses.applyCors(exchange, config);
            exchange.getResponseHeaders().set("Allow", "GET, HEAD, OPTIONS");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            HttpResponses.methodNotAllowed(exchange, config);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path == null) {
            HttpResponses.notFound(exchange, config);
            return;
        }
        String relative = relativePath(path);
        if (relative == null) {
            HttpResponses.notFound(exchange, config);
            return;
        }
        if (relative.isEmpty() || relative.endsWith("/")) {
            relative = defaultFile;
        }

        String resource = classpathRoot + "/" + relative;
        try (InputStream in = openResource(resource)) {
            if (in == null) {
                HttpResponses.notFound(exchange, config);
                return;
            }
            byte[] bytes = in.readAllBytes();
            String contentType = contentType(relative);
            HttpResponses.applyCors(exchange, config);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            if ("HEAD".equals(method)) {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Resolves the request path to a relative resource under the classpath root.
     *
     * @return relative path, or null if the request is outside the prefix or illegal
     */
    String relativePath(String requestPath) {
        if (!requestPath.equals(urlPrefix) && !requestPath.startsWith(urlPrefix + "/")) {
            return null;
        }
        String rel = requestPath.equals(urlPrefix)
                ? ""
                : requestPath.substring(urlPrefix.length() + 1);
        if (rel.contains("..") || rel.startsWith("/") || rel.contains("\\")) {
            return null;
        }
        return rel;
    }

    private static InputStream openResource(String resource) {
        ClassLoader cl = StaticFileHandler.class.getClassLoader();
        InputStream in = cl.getResourceAsStream(resource);
        if (in != null) {
            return in;
        }
        return StaticFileHandler.class.getResourceAsStream("/" + resource);
    }

    private static String contentType(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
            return "text/javascript; charset=utf-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (lower.endsWith(".map")) {
            return "application/json; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
