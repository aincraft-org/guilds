package com.azoth.territory.web;

import com.azoth.territory.model.LookupResult;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.persist.TerritoryJson;
import com.azoth.territory.persist.TerritoryStore;
import com.azoth.territory.registry.TerritoryRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST API for territory registry (map editor / tooling).
 *
 * <pre>
 * GET    /api/health
 * GET    /api/territories
 * GET    /api/territories/{id}
 * PUT    /api/territories/{id}
 * DELETE /api/territories/{id}
 * GET    /api/resolve?world=&amp;x=&amp;z=
 * GET    /api/meta   (proxy/tls info for the UI)
 * </pre>
 */
public final class TerritoryApiHandler implements HttpHandler {
    private final WebConfig config;
    private final ReverseProxySupport proxy;
    private final TerritoryRegistry registry;
    private final TerritoryJson json;
    private final Supplier<TerritoryStore> storeSupplier;
    private final Logger log;

    public TerritoryApiHandler(
            WebConfig config,
            ReverseProxySupport proxy,
            TerritoryRegistry registry,
            TerritoryJson json,
            Supplier<TerritoryStore> storeSupplier,
            Logger log
    ) {
        this.config = config;
        this.proxy = proxy;
        this.registry = registry;
        this.json = json;
        this.storeSupplier = storeSupplier;
        this.log = log;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            if ("OPTIONS".equals(method)) {
                HttpResponses.applyCors(exchange, config);
                exchange.getResponseHeaders().set("Allow", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String path = normalizePath(exchange.getRequestURI().getPath());
            if (path.startsWith("/api/")) {
                path = path.substring("/api".length());
            }

            // Auth for mutating methods (and optionally all if token set)
            if (config.requiresAuth() && needsAuth(method, path)) {
                if (!authorized(exchange)) {
                    HttpResponses.unauthorized(exchange, config);
                    return;
                }
            }

            if ("/health".equals(path) && "GET".equals(method)) {
                health(exchange);
                return;
            }
            if ("/meta".equals(path) && "GET".equals(method)) {
                meta(exchange);
                return;
            }
            if ("/territories".equals(path) && "GET".equals(method)) {
                list(exchange);
                return;
            }
            if ("/territories".equals(path) && ("POST".equals(method) || "PUT".equals(method))) {
                upsertBody(exchange);
                return;
            }
            if (path.startsWith("/territories/")) {
                String id = path.substring("/territories/".length());
                if (id.contains("/")) {
                    HttpResponses.notFound(exchange, config);
                    return;
                }
                switch (method) {
                    case "GET" -> getOne(exchange, id);
                    case "PUT", "POST" -> upsert(exchange, id);
                    case "DELETE" -> delete(exchange, id);
                    default -> HttpResponses.methodNotAllowed(exchange, config);
                }
                return;
            }
            if ("/resolve".equals(path) && "GET".equals(method)) {
                resolve(exchange);
                return;
            }

            HttpResponses.notFound(exchange, config);
        } catch (IllegalArgumentException e) {
            HttpResponses.badRequest(exchange, e.getMessage(), config);
        } catch (Exception e) {
            log.log(Level.WARNING, "API error", e);
            HttpResponses.serverError(exchange, e.getMessage(), config);
        }
    }

    private boolean needsAuth(String method, String path) {
        if ("GET".equals(method) && ("/health".equals(path) || "/meta".equals(path))) {
            return false;
        }
        // When a token is configured, require it for all API calls except health/meta
        return true;
    }

    private boolean authorized(HttpExchange exchange) {
        String expected = config.apiToken();
        String header = exchange.getRequestHeaders().getFirst("X-Api-Token");
        if (header != null && header.equals(expected)) {
            return true;
        }
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null) {
            String a = auth.trim();
            if (a.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return a.substring(7).trim().equals(expected);
            }
            if (a.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private void health(HttpExchange exchange) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("status", "ok");
        o.addProperty("service", "azoth-territory");
        o.addProperty("territories", registry.size());
        o.addProperty("tls", config.https());
        o.addProperty("secure", proxy.isSecure(exchange));
        HttpResponses.json(exchange, 200, json.gson().toJson(o), config);
    }

    private void meta(HttpExchange exchange) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("publicOrigin", proxy.publicOrigin(exchange));
        o.addProperty("scheme", proxy.scheme(exchange));
        o.addProperty("host", proxy.host(exchange));
        o.addProperty("trustProxy", config.trustProxy());
        o.addProperty("tlsEnabled", config.https());
        o.addProperty("secure", proxy.isSecure(exchange));
        proxy.clientIp(exchange).ifPresent(ip -> o.addProperty("clientIp", ip));
        o.addProperty("authRequired", config.requiresAuth());
        HttpResponses.json(exchange, 200, json.gson().toJson(o), config);
    }

    private void list(HttpExchange exchange) throws IOException {
        HttpResponses.json(exchange, 200, json.gson().toJson(json.registryToJson(registry)), config);
    }

    private void getOne(HttpExchange exchange, String id) throws IOException {
        Optional<Territory> t = registry.get(id);
        if (t.isEmpty()) {
            HttpResponses.notFound(exchange, config);
            return;
        }
        HttpResponses.json(exchange, 200, json.gson().toJson(json.toJson(t.get())), config);
    }

    private void upsertBody(HttpExchange exchange) throws IOException {
        String body = HttpResponses.readBody(exchange);
        Territory t = json.fromJsonString(body);
        registry.register(t);
        persistQuietly();
        HttpResponses.json(exchange, 200, json.gson().toJson(json.toJson(t)), config);
    }

    private void upsert(HttpExchange exchange, String id) throws IOException {
        String body = HttpResponses.readBody(exchange);
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        if (!o.has("id")) {
            o.addProperty("id", id);
        } else if (!id.equals(o.get("id").getAsString())) {
            throw new IllegalArgumentException("path id does not match body id");
        }
        Territory t = json.fromJson(o);
        registry.register(t);
        persistQuietly();
        HttpResponses.json(exchange, 200, json.gson().toJson(json.toJson(t)), config);
    }

    private void delete(HttpExchange exchange, String id) throws IOException {
        boolean removed = registry.unregister(id);
        if (!removed) {
            HttpResponses.notFound(exchange, config);
            return;
        }
        persistQuietly();
        HttpResponses.noContent(exchange, config);
    }

    private void resolve(HttpExchange exchange) throws IOException {
        Map<String, String> q = HttpResponses.queryParams(exchange);
        String world = q.getOrDefault("world", "world");
        if (!q.containsKey("x") || !q.containsKey("z")) {
            throw new IllegalArgumentException("x and z query params required");
        }
        int x = Integer.parseInt(q.get("x"));
        int z = Integer.parseInt(q.get("z"));
        LookupResult result = registry.resolve(world, x, z);
        JsonObject o = new JsonObject();
        o.addProperty("world", world);
        o.addProperty("x", x);
        o.addProperty("z", z);
        o.addProperty("contained", result.isContained());
        if (result.isContained()) {
            o.addProperty("territoryId", result.territoryId().orElseThrow());
            result.territory().ifPresent(t -> o.addProperty("territoryName", t.name()));
            ZoneType type = result.zoneType().orElse(ZoneType.WILDERNESS);
            o.addProperty("zoneType", type.name());
            result.zone().ifPresent(zone -> {
                if (zone.zoneId() != null) {
                    o.addProperty("zoneId", zone.zoneId());
                }
                o.addProperty("zoneName", zone.zoneName());
                o.addProperty("defaultZone", zone.isDefault());
            });
            result.territory().ifPresent(t -> o.add("government", json.governmentToJson(t.government())));
        }
        HttpResponses.json(exchange, 200, json.gson().toJson(o), config);
    }

    private void persistQuietly() {
        TerritoryStore store = storeSupplier.get();
        if (store == null) {
            return;
        }
        try {
            store.save(registry);
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to persist after API mutation", e);
        }
    }

    static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
