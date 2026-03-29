package org.aincraft.towny.services;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Service interface for managing town chat system
 */
public interface ChatService {

    /**
     * Send a message to all residents of a town
     * @param townId The town ID
     * @param sender The player sending the message
     * @param message The message to send
     */
    void sendTownChat(String townId, Player sender, String message);

    /**
     * Check if a player has town chat enabled
     * @param playerUuid The player's UUID
     * @return true if town chat is enabled for this player
     */
    boolean isTownChatEnabled(UUID playerUuid);

    /**
     * Toggle town chat for a player
     * @param playerUuid The player's UUID
     * @param enabled Whether town chat should be enabled or disabled
     */
    void setTownChatEnabled(UUID playerUuid, boolean enabled);

    /**
     * Check if a player has admin spy enabled for town chat
     * @param playerUuid The player's UUID
     * @return true if admin spy is enabled for this player
     */
    boolean isAdminSpy(UUID playerUuid);

    /**
     * Toggle admin spy for town chat for a player
     * @param playerUuid The player's UUID
     * @param enabled Whether admin spy should be enabled or disabled
     */
    void setAdminSpy(UUID playerUuid, boolean enabled);
}