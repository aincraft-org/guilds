package org.aincraft.guilds.web;



import org.aincraft.guilds.models.Guild;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages browser sessions for the tech tree web interface.
 */

public class SessionManager {

    private final WebServerConfig config;
    private final Logger logger;
    private final Map<String, TechTreeSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    public SessionManager(WebServerConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;

        // Schedule cleanup of expired sessions every 5 minutes
        scheduler.scheduleAtFixedRate(this::cleanExpiredSessions, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Creates a new session for the given player and guild
     */
    public TechTreeSession createSession(Player player, Guild guild) {
        TechTreeSession session = new TechTreeSession(player, guild, config.getSessionTimeoutMinutes());
        sessions.put(session.getSessionId(), session);
        logger.info("Created tech tree session: " + session.getSessionId() +
                   " for player: " + session.getPlayerName() +
                   " town: " + session.getGuildName());
        return session;
    }

    /**
     * Retrieves a session by ID if it exists and is not expired
     */
    public Optional<TechTreeSession> getSession(String sessionId) {
        TechTreeSession session = sessions.get(sessionId);
        if (session != null) {
            if (session.isExpired()) {
                invalidateSession(sessionId);
                return Optional.empty();
            }
            return Optional.of(session);
        }
        return Optional.empty();
    }

    /**
     * Invalidates and removes a session
     */
    public void invalidateSession(String sessionId) {
        TechTreeSession removed = sessions.remove(sessionId);
        if (removed != null) {
            logger.info("Invalidated tech tree session: " + sessionId);
        }
    }

    /**
     * Cleans up expired sessions
     */
    public void cleanExpiredSessions() {
        int initialSize = sessions.size();
        sessions.entrySet().removeIf(entry -> {
            TechTreeSession session = entry.getValue();
            if (session.isExpired()) {
                logger.fine("Cleaning up expired tech tree session: " + session.getSessionId());
                return true;
            }
            return false;
        });

        int removed = initialSize - sessions.size();
        if (removed > 0) {
            logger.info("Cleaned up " + removed + " expired tech tree sessions");
        }
    }


    /**
     * Shuts down the session manager and cleans up all resources
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        sessions.clear();
        logger.info("SessionManager shutdown complete");
    }
}