package org.aincraft.guilds.services;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Service interface for managing guild chat system
 */
public interface ChatService {

    /**
     * Send a message to all residents of a guild
     * @param guildId The guild ID
     * @param sender The player sending the message
     * @param message The message to send
     */
    void sendGuildChat(String guildId, Player sender, String message);

    /**
     * Check if a player has guild chat enabled
     * @param playerUuid The player's UUID
     * @return true if guild chat is enabled for this player
     */
    boolean isGuildChatEnabled(UUID playerUuid);

    /**
     * Toggle guild chat for a player
     * @param playerUuid The player's UUID
     * @param enabled Whether guild chat should be enabled or disabled
     */
    void setGuildChatEnabled(UUID playerUuid, boolean enabled);

    /**
     * Check if a player has admin spy enabled for guild chat
     * @param playerUuid The player's UUID
     * @return true if admin spy is enabled for this player
     */
    boolean isAdminSpy(UUID playerUuid);

    /**
     * Toggle admin spy for guild chat for a player
     * @param playerUuid The player's UUID
     * @param enabled Whether admin spy should be enabled or disabled
     */
    void setAdminSpy(UUID playerUuid, boolean enabled);
}