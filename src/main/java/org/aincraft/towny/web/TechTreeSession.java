package org.aincraft.towny.web;

import org.aincraft.towny.models.Town;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a browser session for interacting with the tech tree web interface.
 */
public class TechTreeSession {
    
    private final String sessionId;
    private final UUID playerUuid;
    private final String playerName;
    private final String townId;
    private final String townName;
    private final Instant createdAt;
    private Instant expiresAt;
    private boolean confirmed;
    
    public TechTreeSession(Player player, Town town, int timeoutMinutes) {
        this.sessionId = UUID.randomUUID().toString();
        this.playerUuid = player.getUniqueId();
        this.playerName = player.getName();
        this.townId = town.getId();
        this.townName = town.getName();
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plusSeconds(timeoutMinutes * 60L);
        this.confirmed = false;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public UUID getPlayerUuid() {
        return playerUuid;
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
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    public void extendExpiration(int additionalMinutes) {
        this.expiresAt = this.expiresAt.plusSeconds(additionalMinutes * 60L);
    }
}