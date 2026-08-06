package org.aincraft.guilds.web;

import org.aincraft.guilds.models.Guild;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a browser session for interacting with the tech tree web interface.
 */
public class TechTreeSession {

    private final String sessionId;
    private final String playerName;
    private final String guildId;
    private final String guildName;
    private final Instant expiresAt;

    public TechTreeSession(Player player, Guild guild, int timeoutMinutes) {
        this.sessionId = UUID.randomUUID().toString();
        this.playerName = player.getName();
        this.guildId = guild.getId();
        this.guildName = guild.getName();
        this.expiresAt = Instant.now().plusSeconds(timeoutMinutes * 60L);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getGuildName() {
        return guildName;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
