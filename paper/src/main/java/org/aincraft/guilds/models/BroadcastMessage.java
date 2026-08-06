package org.aincraft.guilds.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a broadcast message for guild communication
 */
public class BroadcastMessage {

    private String id;
    private String guildId;
    private String messageType;
    private String title;
    private String content;
    private UUID senderUuid;
    private String senderName;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean isActive;
    private int priority;
    private String targetAudience; // all, residents, assistants, mayor

    /**
     * Default constructor for database mapping
     */
    public BroadcastMessage() {
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.priority = 1; // Default priority
        this.targetAudience = "all";
    }

    /**
     * Constructor for creating a new broadcast message
     * @param guildId Guild ID this broadcast belongs to
     * @param messageType Type of message (announcement, alert, welcome)
     * @param title Message title
     * @param content Message content
     * @param senderUuid UUID of the sender
     * @param senderName Name of the sender
     */
    public BroadcastMessage(String guildId, String messageType, String title, String content,
                           UUID senderUuid, String senderName) {
        this();
        this.id = UUID.randomUUID().toString();
        this.guildId = guildId;
        this.messageType = messageType;
        this.title = title;
        this.content = content;
        this.senderUuid = senderUuid;
        this.senderName = senderName;
    }

    /**
     * Broadcast message types
     */
    public static class Type {
        public static final String ANNOUNCEMENT = "announcement";
        public static final String ALERT = "alert";
        public static final String WELCOME = "welcome";
        public static final String WARNING = "warning";
        public static final String CELEBRATION = "celebration";
        public static final String ECONOMIC = "economic";
    }

    /**
     * Target audience types
     */
    public static class Audience {
        public static final String ALL = "all";
        public static final String RESIDENTS = "residents";
        public static final String ASSISTANTS = "assistants";
        public static final String MAYOR = "mayor";
    }

    // Priority levels (higher number = higher priority)
    public static class Priority {
        public static final int MIN = 1;
        public static final int MAX = 5;

        public static final int LOW = 1;
        public static final int NORMAL = 2;
        public static final int HIGH = 3;
        public static final int URGENT = 4;
        public static final int CRITICAL = 5;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    public void setSenderUuid(UUID senderUuid) {
        this.senderUuid = senderUuid;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = Math.max(Priority.MIN, Math.min(Priority.MAX, priority));
    }

    public String getTargetAudience() {
        return targetAudience;
    }

    public void setTargetAudience(String targetAudience) {
        this.targetAudience = targetAudience;
    }

    /**
     * Check if this broadcast has expired
     * @return True if expired, false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if this broadcast should be displayed (active and not expired)
     * @return True if should be displayed, false otherwise
     */
    public boolean shouldDisplay() {
        return isActive && !isExpired();
    }

    /**
     * Set this broadcast to expire after a certain number of days
     * @param days Number of days until expiration
     */
    public void setExpirationInDays(int days) {
        this.expiresAt = createdAt.plusDays(days);
    }

    /**
     * Set this broadcast to expire after a certain number of hours
     * @param hours Number of hours until expiration
     */
    public void setExpirationInHours(int hours) {
        this.expiresAt = createdAt.plusHours(hours);
    }

    /**
     * Archive this broadcast (deactivate it)
     */
    public void archive() {
        this.isActive = false;
    }

    @Override
    public String toString() {
        return "BroadcastMessage{" +
                "id='" + id + '\'' +
                ", townId='" + guildId + '\'' +
                ", messageType='" + messageType + '\'' +
                ", title='" + title + '\'' +
                ", senderName='" + senderName + '\'' +
                ", createdAt=" + createdAt +
                ", priority=" + priority +
                ", active=" + isActive +
                '}';
    }
}