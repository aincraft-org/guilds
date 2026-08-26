package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.models.BroadcastMessage;
import dev.mintychochip.guilds.models.Guild;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing guild broadcast messages
 */
public interface BroadcastService {

    /**
     * Create a new broadcast message
     * @param guildId Guild ID this broadcast belongs to
     * @param messageType Type of message (announcement, alert, welcome)
     * @param title Message title
     * @param content Message content
     * @param senderUuid UUID of the sender
     * @param senderName Name of the sender
     * @return Created broadcast message
     */
    BroadcastMessage createBroadcast(String guildId, String messageType, String title, String content,
                                    UUID senderUuid, String senderName);

    /**
     * Get a broadcast message by ID
     * @param broadcastId Broadcast ID
     * @return Broadcast message if found
     */
    Optional<BroadcastMessage> getBroadcast(String broadcastId);

    /**
     * Get all active broadcasts for a guild
     * @param guildId Guild ID
     * @return List of active broadcasts
     */
    List<BroadcastMessage> getActiveBroadcasts(String guildId);

    /**
     * Get all broadcasts for a guild (including inactive)
     * @param guildId Guild ID
     * @return List of all broadcasts
     */
    List<BroadcastMessage> getAllBroadcasts(String guildId);

    /**
     * Get broadcasts for a guild filtered by target audience
     * @param guildId Guild ID
     * @param audience Target audience (all, residents, assistants, mayor)
     * @return List of broadcasts for the specified audience
     */
    List<BroadcastMessage> getBroadcastsByAudience(String guildId, String audience);

    /**
     * Get broadcasts for a guild filtered by message type
     * @param guildId Guild ID
     * @param messageType Message type
     * @return List of broadcasts of the specified type
     */
    List<BroadcastMessage> getBroadcastsByType(String guildId, String messageType);

    /**
     * Get broadcasts that should be displayed to a specific player
     * @param guildId Guild ID
     * @param playerUuid Player UUID
     * @param playerRole Player role in the guild (mayor, assistant, resident)
     * @return List of broadcasts visible to this player
     */
    List<BroadcastMessage> getBroadcastsForPlayer(String guildId, UUID playerUuid, String playerRole);

    /**
     * Update an existing broadcast message
     * @param broadcast Broadcast message to update
     * @return True if update was successful
     */
    boolean updateBroadcast(BroadcastMessage broadcast);

    /**
     * Archive/deactivate a broadcast message
     * @param broadcastId Broadcast ID to archive
     * @return True if archiving was successful
     */
    boolean archiveBroadcast(String broadcastId);

    /**
     * Delete a broadcast message permanently
     * @param broadcastId Broadcast ID to delete
     * @return True if deletion was successful
     */
    boolean deleteBroadcast(String broadcastId);

    /**
     * Create a welcome message for new guild residents
     * @param guildId Guild ID
     * @param newResidentName Name of the new resident
     * @return Created welcome message
     */
    BroadcastMessage createWelcomeMessage(String guildId, String newResidentName);

    /**
     * Create an alert message for important guild events
     * @param guildId Guild ID
     * @param alertTitle Alert title
     * @param alertContent Alert content
     * @param senderUuid Sender UUID
     * @param senderName Sender name
     * @param priority Priority level (1-5)
     * @return Created alert message
     */
    BroadcastMessage createAlertMessage(String guildId, String alertTitle, String alertContent,
                                       UUID senderUuid, String senderName, int priority);

    /**
     * Create a general announcement
     * @param guildId Guild ID
     * @param title Announcement title
     * @param content Announcement content
     * @param senderUuid Sender UUID
     * @param senderName Sender name
     * @param expirationDays Days until message expires (0 for no expiration)
     * @return Created announcement
     */
    BroadcastMessage createAnnouncement(String guildId, String title, String content,
                                      UUID senderUuid, String senderName, int expirationDays);

    /**
     * Clean up expired broadcast messages
     * @param guildId Guild ID (null to clean up all guilds)
     * @return Number of cleaned up messages
     */
    int cleanupExpiredBroadcasts(String guildId);

    /**
     * Get broadcast statistics for a guild
     * @param guildId Guild ID
     * @return Broadcast statistics
     */
    BroadcastStatistics getBroadcastStatistics(String guildId);

    /**
     * Check if a player can create broadcasts of a certain type
     * @param playerUuid Player UUID
     * @param guildId Guild ID
     * @param messageType Message type to check
     * @return True if player can create broadcasts of this type
     */
    boolean canCreateBroadcast(UUID playerUuid, String guildId, String messageType);

    /**
     * Send a broadcast to all online guild members
     * @param broadcast Broadcast message to send
     * @return Number of players the message was sent to
     */
    int sendBroadcastToOnlineMembers(BroadcastMessage broadcast);

    /**
     * Broadcast statistics data class
     */
    class BroadcastStatistics {
        /** The total broadcasts. */
        private final int totalBroadcasts;
        /** The active broadcasts. */
        private final int activeBroadcasts;
        /** The expired broadcasts. */
        private final int expiredBroadcasts;
        /** The announcements. */
        private final int announcements;
        /** The alerts. */
        private final int alerts;
        /** The welcome messages. */
        private final int welcomeMessages;
        /** The last broadcast. */
        private final LocalDateTime lastBroadcast;
        /** The most active message type. */
        private final String mostActiveMessageType;

        /**
         * Creates a new broadcast statistics instance.
         * @param totalBroadcasts the total broadcasts
         * @param activeBroadcasts the active broadcasts
         * @param expiredBroadcasts the expired broadcasts
         * @param announcements the announcements
         * @param alerts the alerts
         * @param welcomeMessages the welcome messages
         * @param lastBroadcast the last broadcast
         * @param mostActiveMessageType the most active message type
         */
        public BroadcastStatistics(int totalBroadcasts, int activeBroadcasts, int expiredBroadcasts,
                                  int announcements, int alerts, int welcomeMessages,
                                  LocalDateTime lastBroadcast, String mostActiveMessageType) {
            this.totalBroadcasts = totalBroadcasts;
            this.activeBroadcasts = activeBroadcasts;
            this.expiredBroadcasts = expiredBroadcasts;
            this.announcements = announcements;
            this.alerts = alerts;
            this.welcomeMessages = welcomeMessages;
            this.lastBroadcast = lastBroadcast;
            this.mostActiveMessageType = mostActiveMessageType;
        }

        /**
         * Returns the total broadcasts.
         * @return the result
         */
        public int getTotalBroadcasts() { return totalBroadcasts; }
        /**
         * Returns the active broadcasts.
         * @return the result
         */
        public int getActiveBroadcasts() { return activeBroadcasts; }
        /**
         * Returns the expired broadcasts.
         * @return the result
         */
        public int getExpiredBroadcasts() { return expiredBroadcasts; }
        /**
         * Returns the announcements.
         * @return the result
         */
        public int getAnnouncements() { return announcements; }
        /**
         * Returns the alerts.
         * @return the result
         */
        public int getAlerts() { return alerts; }
        /**
         * Returns the welcome messages.
         * @return the result
         */
        public int getWelcomeMessages() { return welcomeMessages; }
        /**
         * Returns the last broadcast.
         * @return the result
         */
        public LocalDateTime getLastBroadcast() { return lastBroadcast; }
        /**
         * Returns the most active message type.
         * @return the result
         */
        public String getMostActiveMessageType() { return mostActiveMessageType; }
    }
}