package org.aincraft.guilds.web;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinGson;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.services.TownService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Web server for the tech tree system that provides REST API and WebSocket endpoints.
 */

public class WebServer {

    private final TechTreeService techTreeService;
    private final TownService townService;
    private final SessionManager sessionManager;
    private final WebServerConfig config;
    private final Logger logger;
    private final Gson gson = new Gson();

    // Store pending unlocks per session
    private final ConcurrentHashMap<String, Set<String>> pendingUnlocks = new ConcurrentHashMap<>();

    private Javalin app;


    public WebServer(TechTreeService techTreeService, TownService townService,
                    SessionManager sessionManager, WebServerConfig config, Logger logger) {
        this.techTreeService = techTreeService;
        this.townService = townService;
        this.sessionManager = sessionManager;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Start the web server on the configured port
     */
    public void start() {
        if (!config.isEnabled()) {
            logger.info("Web server is disabled in configuration");
            return;
        }

        try {
            app = Javalin.create(javalin -> {
                javalin.jsonMapper(new JavalinGson(gson, true));
                // No CORS policy is configured by the plugin; leave Javalin's
                // default (no CORS headers) rather than broadening access.
            });

            setupRoutes();
            setupWebSocket();

            int port = config.getPort();
            app.start(port);
            logger.info("Tech tree web server started on port " + port);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start web server", e);
        }
    }

    /**
     * Stop the web server
     */
    public void stop() {
        if (app != null) {
            app.stop();
            logger.info("Tech tree web server stopped");
        }
    }

    private void setupRoutes() {
        // Health check endpoint
        app.get("/api/health", ctx -> {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ok");
            response.put("online", true);
            ctx.json(response);
        });

        // Get session info
        app.get("/api/session/{sessionId}", ctx -> {
            String sessionId = ctx.pathParam("sessionId");

            Optional<TechTreeSession> sessionOpt = sessionManager.getSession(sessionId);
            if (sessionOpt.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(Map.of("error", "Invalid or expired session"));
                return;
            }

            TechTreeSession session = sessionOpt.get();

            // Get town data
            Optional<Town> townOpt = townService.getTown(session.getTownId());
            if (townOpt.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(Map.of("error", "Town not found"));
                return;
            }

            Town town = townOpt.get();
            techTreeService.loadTownTechData(town);

            Map<String, Object> response = new HashMap<>();
            response.put("session", Map.of(
                "playerName", session.getPlayerName(),
                "townName", session.getTownName(),
                "expiresAt", session.getExpiresAt().toString()
            ));
            response.put("town", Map.of(
                "name", town.getName(),
                "level", town.getTownLevel(),
                "techPoints", town.getTechPoints()
            ));

            ctx.json(response);
        });
    }

    private void setupWebSocket() {
        app.ws("/ws/session/{sessionId}", ws -> {
            ws.onConnect(ctx -> handleWebSocketConnect(ctx));
            ws.onMessage(ctx -> handleWebSocketMessage(ctx));
            ws.onClose(ctx -> handleWebSocketClose(ctx));
            ws.onError(ctx -> handleWebSocketError(ctx));
        });
    }

    private void handleWebSocketConnect(WsContext ctx) {
        String sessionId = ctx.pathParam("sessionId");

        Optional<TechTreeSession> sessionOpt = sessionManager.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            ctx.send("{\"error\":\"Invalid or expired session\"}");
            ctx.closeSession();
            return;
        }

        TechTreeSession session = sessionOpt.get();

        // Get town data
        Optional<Town> townOpt = townService.getTown(session.getTownId());
        if (townOpt.isEmpty()) {
            ctx.send("{\"error\":\"Town not found\"}");
            ctx.closeSession();
            return;
        }

        Town town = townOpt.get();
        techTreeService.loadTownTechData(town);

        // Initialize pending unlocks for this session
        pendingUnlocks.putIfAbsent(sessionId, new HashSet<>());

        // Send initial tree state
        ctx.send(buildTreeStateJson(session, town));
    }

    private void handleWebSocketMessage(WsMessageContext ctx) {
        String message = ctx.message();
        String sessionId = ctx.pathParam("sessionId");

        try {
            Map<String, Object> messageData =
                    gson.fromJson(message, new TypeToken<Map<String, Object>>() {}.getType());
            String action = (String) messageData.get("action");

            Optional<TechTreeSession> sessionOpt = sessionManager.getSession(sessionId);
            if (sessionOpt.isEmpty()) {
                ctx.send("{\"error\":\"Session expired\"}");
                ctx.closeSession();
                return;
            }

            TechTreeSession session = sessionOpt.get();
            Optional<Town> townOpt = townService.getTown(session.getTownId());
            if (townOpt.isEmpty()) {
                ctx.send("{\"error\":\"Town not found\"}");
                ctx.closeSession();
                return;
            }

            Town town = townOpt.get();
            techTreeService.loadTownTechData(town);

            Set<String> pending = pendingUnlocks.getOrDefault(sessionId, new HashSet<>());

            switch (action) {
                case "unlock":
                    handleUnlockAction(ctx, session, town, messageData, pending);
                    break;
                case "confirm":
                    handleConfirmAction(ctx, session, town, pending);
                    break;
                case "cancel":
                    handleCancelAction(ctx, sessionId, pending);
                    break;
                default:
                    ctx.send("{\"error\":\"Unknown action\"}");
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Malformed WebSocket message in session " + sessionId, e);
            ctx.send("{\"error\":\"Invalid message format\"}");
        }
    }

    private void handleUnlockAction(WsContext ctx, TechTreeSession session, Town town,
                                   Map<String, Object> messageData, Set<String> pending) {
        String nodeId = (String) messageData.get("nodeId");
        if (nodeId == null) {
            ctx.send("{\"error\":\"nodeId required\"}");
            return;
        }

        Optional<TechTreeNode> nodeOpt = techTreeService.getNode(nodeId);
        if (nodeOpt.isEmpty()) {
            ctx.send("{\"error\":\"Invalid node ID\"}");
            return;
        }

        TechTreeNode node = nodeOpt.get();

        if (!techTreeService.canUnlockNode(town, nodeId)) {
            ctx.send("{\"error\":\"Cannot unlock this node\"}");
            return;
        }

        // Add to pending
        pending.add(nodeId);

        // Send updated state
        ctx.send(buildTreeStateJson(session, town));
    }

    private void handleConfirmAction(WsContext ctx, TechTreeSession session, Town town, Set<String> pending) {
        if (pending.isEmpty()) {
            ctx.send("{\"error\":\"No pending unlocks\"}");
            return;
        }

        int totalCost = 0;
        for (String nodeId : pending) {
            Optional<TechTreeNode> nodeOpt = techTreeService.getNode(nodeId);
            if (nodeOpt.isPresent()) {
                totalCost += nodeOpt.get().getCost();
            }
        }

        if (town.getTechPoints() < totalCost) {
            ctx.send("{\"error\":\"Insufficient tech points\"}");
            return;
        }

        // Apply all pending unlocks
        for (String nodeId : pending) {
            techTreeService.unlockTechNode(town, nodeId);
        }

        // Save town data
        techTreeService.saveTownTechData(town);

        // Clear pending
        pending.clear();

        // Send final state and close
        ctx.send(buildTreeStateJson(session, town));
        ctx.closeSession();
    }

    private void handleCancelAction(WsContext ctx, String sessionId, Set<String> pending) {
        pending.clear();
        pendingUnlocks.remove(sessionId);
        ctx.send("{\"status\":\"cancelled\"}");
        ctx.closeSession();
    }

    private void handleWebSocketClose(WsContext ctx) {
        String sessionId = ctx.pathParam("sessionId");
        pendingUnlocks.remove(sessionId);
    }

    private void handleWebSocketError(WsContext ctx) {
        logger.warning("WebSocket error in session: " + ctx.pathParam("sessionId"));
    }

    private String buildTreeStateJson(TechTreeSession session, Town town) {
        Map<String, Object> treeState = new HashMap<>();
        treeState.put("type", "tree_state");

        Map<String, Object> payload = new HashMap<>();

        // Session info
        payload.put("session", Map.of(
            "playerName", session.getPlayerName(),
            "townName", session.getTownName(),
            "expiresAt", session.getExpiresAt().toString()
        ));

        // Town info
        payload.put("town", Map.of(
            "name", town.getName(),
            "level", town.getTownLevel(),
            "techPoints", town.getTechPoints()
        ));

        // Nodes
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (TechTreeNode node : techTreeService.getAllNodes()) {
            Map<String, Object> nodeData = new HashMap<>();
            nodeData.put("id", node.getId());
            nodeData.put("name", node.getName());
            nodeData.put("branch", node.getBranch() != null ? node.getBranch().name() : "INFRASTRUCTURE");
            nodeData.put("cost", node.getCost());
            nodeData.put("positionX", node.getPositionX());
            nodeData.put("positionY", node.getPositionY());
            nodeData.put("description", node.getDescription());
            nodeData.put("prerequisites", node.getPrerequisites() != null ? node.getPrerequisites() : new ArrayList<>());

            // Determine node status
            String status = "locked";
            if (town.isTechNodeUnlocked(node.getId())) {
                status = "unlocked";
            } else if (techTreeService.canUnlockNode(town, node.getId())) {
                status = "available";
            }

            // Check if pending unlock
            Set<String> pending = pendingUnlocks.getOrDefault(session.getSessionId(), new HashSet<>());
            if (pending.contains(node.getId())) {
                status = "pending";
            }

            nodeData.put("status", status);
            nodes.add(nodeData);
        }
        payload.put("nodes", nodes);

        // Edges (placeholder for now)
        List<Map<String, Object>> edges = new ArrayList<>();
        payload.put("edges", edges);

        // Pending unlocks
        Set<String> pending = pendingUnlocks.getOrDefault(session.getSessionId(), new HashSet<>());
        payload.put("pendingUnlocks", new ArrayList<>(pending));

        treeState.put("payload", payload);

        return gson.toJson(treeState);
    }
}