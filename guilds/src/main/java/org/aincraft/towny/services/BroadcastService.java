package org.aincraft.towny.services;

import org.aincraft.towny.models.BroadcastMessage;
import org.aincraft.towny.models.Town;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing town broadcast messages
 */
public interface BroadcastService {

    /**
     * Create a new broadcast message
     * @param townId Town ID this broadcast belongs to
     * @param messageType Type of message (announcement, alert, welcome)
     * @param title Message title
     * @param content Message content
     * @param senderUuid UUID of the sender
     * @param senderName Name of the sender
     * @return Created broadcast message
     */
    BroadcastMessage createBroadcast(String townId, String messageType, String title, String content,
                                    UUID senderUuid, String senderName);

    /**
     * Get a broadcast message by ID
     * @param broadcastId Broadcast ID
     * @return Broadcast message if found
     */
    Optional<BroadcastMessage> getBroadcast(String broadcastId);

    /**
     * Get all active broadcasts for a town
     * @param townId Town ID
     * @return List of active broadcasts
     */
    List<BroadcastMessage> getActiveBroadcasts(String townId);

    /**
     * Get all broadcasts for a town (including inactive)
     * @param townId Town ID
     * @return List of all broadcasts
     */
    List<BroadcastMessage> getAllBroadcasts(String townId);

    /**
     * Get broadcasts for a town filtered by target audience
     * @param townId Town ID
     * @param audience Target audience (all, residents, assistants, mayor)
     * @return List of broadcasts for the specified audience
     */
    List<BroadcastMessage> getBroadcastsByAudience(String townId, String audience);

    /**
     * Get broadcasts for a town filtered by message type
     * @param townId Town ID
     * @param messageType Message type
     * @return List of broadcasts of the specified type
     */
    List<BroadcastMessage> getBroadcastsByType(String townId, String messageType);

    /**
     * Get broadcasts that should be displayed to a specific player
     * @param townId Town ID
     * @param playerUuid Player UUID
     * @param playerRole Player role in the town (mayor, assistant, resident)
     * @return List of broadcasts visible to this player
     */
    List<BroadcastMessage> getBroadcastsForPlayer(String townId, UUID playerUuid, String playerRole);

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
     * Create a welcome message for new town residents
     * @param townId Town ID
     * @param newResidentName Name of the new resident
     * @return Created welcome message
     */
    BroadcastMessage createWelcomeMessage(String townId, String newResidentName);

    /**
     * Create an alert message for important town events
     * @param townId Town ID
     * @param alertTitle Alert title
     * @param alertContent Alert content
     * @param senderUuid Sender UUID
     * @param senderName Sender name
     * @param priority Priority level (1-5)
     * @return Created alert message
     */
    BroadcastMessage createAlertMessage(String townId, String alertTitle, String alertContent,
                                       UUID senderUuid, String senderName, int priority);

    /**
     * Create a general announcement
     * @param townId Town ID
     * @param title Announcement title
     * @param content Announcement content
     * @param senderUuid Sender UUID
     * @param senderName Sender name
     * @param expirationDays Days until message expires (0 for no expiration)
     * @return Created announcement
     */
    BroadcastMessage createAnnouncement(String townId, String title, String content,
                                      UUID senderUuid, String senderName, int expirationDays);

    /**
     * Clean up expired broadcast messages
     * @param townId Town ID (null to clean up all towns)
     * @return Number of cleaned up messages
     */
    int cleanupExpiredBroadcasts(String townId);

    /**
     * Get broadcast statistics for a town
     * @param townId Town ID
     * @return Broadcast statistics
     */
    BroadcastStatistics getBroadcastStatistics(String townId);

    /**
     * Check if a player can create broadcasts of a certain type
     * @param playerUuid Player UUID
     * @param townId Town ID
     * @param messageType Message type to check
     * @return True if player can create broadcasts of this type
     */
    boolean canCreateBroadcast(UUID playerUuid, String townId, String messageType);

    /**
     * Send a broadcast to all online town members
     * @param broadcast Broadcast message to send
     * @return Number of players the message was sent to
     */
    int sendBroadcastToOnlineMembers(BroadcastMessage broadcast);

    /**
     * Broadcast statistics data class
     */
    class BroadcastStatistics {
        private final int totalBroadcasts;
        private final int activeBroadcasts;
        private final int expiredBroadcasts;
        private final int announcements;
        private final int alerts;
        private final int welcomeMessages;
        private final LocalDateTime lastBroadcast;
        private final String mostActiveMessageType;

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

        public int getTotalBroadcasts() { return totalBroadcasts; }
        public int getActiveBroadcasts() { return activeBroadcasts; }
        public int getExpiredBroadcasts() { return expiredBroadcasts; }
        public int getAnnouncements() { return announcements; }
        public int getAlerts() { return alerts; }
        public int getWelcomeMessages() { return welcomeMessages; }
        public LocalDateTime getLastBroadcast() { return lastBroadcast; }
        public String getMostActiveMessageType() { return mostActiveMessageType; }
    }
}