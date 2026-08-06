package org.aincraft.guilds.web;

import org.aincraft.guilds.models.Town;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a browser session for interacting with the tech tree web interface.
 */
public class TechTreeSession {

    private final String sessionId;
    private final String playerName;
    private final String townId;
    private final String townName;
    private final Instant expiresAt;

    public TechTreeSession(Player player, Town town, int timeoutMinutes) {
        this.sessionId = UUID.randomUUID().toString();
        this.playerName = player.getName();
        this.townId = town.getId();
        this.townName = town.getName();
        this.expiresAt = Instant.now().plusSeconds(timeoutMinutes * 60L);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getTownId() {
        return townId;
    }

    public String getTownName() {
        return townName;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
