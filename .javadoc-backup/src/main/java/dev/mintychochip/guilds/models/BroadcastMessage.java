package dev.mintychochip.guilds.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a broadcast message for guild communication
 */
public class BroadcastMessage {

    /** The id. */
    private String id;
    /** The guild id. */
    private String guildId;
    /** The message type. */
    private String messageType;
    /** The title. */
    private String title;
    /** The content. */
    private String content;
    /** The sender uuid. */
    private UUID senderUuid;
    /** The sender name. */
    private String senderName;
    /** The created at. */
    private LocalDateTime createdAt;
    /** The expires at. */
    private LocalDateTime expiresAt;
    /** The is active. */
    private boolean isActive;
    /** The priority. */
    private int priority;
    /** The target audience. */
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
        /** The announcement constant. */
        public static final String ANNOUNCEMENT = "announcement";
        /** The alert constant. */
        public static final String ALERT = "alert";
        /** The welcome constant. */
        public static final String WELCOME = "welcome";
        /** The warning constant. */
        public static final String WARNING = "warning";
        /** The celebration constant. */
        public static final String CELEBRATION = "celebration";
        /** The economic constant. */
        public static final String ECONOMIC = "economic";
    }

    /**
     * Target audience types
     */
    public static class Audience {
        /** The all constant. */
        public static final String ALL = "all";
        /** The residents constant. */
        public static final String RESIDENTS = "residents";
        /** The assistants constant. */
        public static final String ASSISTANTS = "assistants";
        /** The mayor constant. */
        public static final String MAYOR = "mayor";
    }

    // Priority levels (higher number = higher priority)
    /** priority. */
    public static class Priority {
        /** The min constant. */
        public static final int MIN = 1;
        /** The max constant. */
        public static final int MAX = 5;

        /** The low constant. */
        public static final int LOW = 1;
        /** The normal constant. */
        public static final int NORMAL = 2;
        /** The high constant. */
        public static final int HIGH = 3;
        /** The urgent constant. */
        public static final int URGENT = 4;
        /** The critical constant. */
        public static final int CRITICAL = 5;
    }

    // Getters and Setters
    /**
     * Returns the id.
     * @return the result
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id.
     * @param id the id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the guild id.
     * @return the result
     */
    public String getGuildId() {
        return guildId;
    }

    /**
     * Sets the guild id.
     * @param guildId the guild id
     */
    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    /**
     * Returns the message type.
     * @return the result
     */
    public String getMessageType() {
        return messageType;
    }

    /**
     * Sets the message type.
     * @param messageType the message type
     */
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    /**
     * Returns the title.
     * @return the result
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the title.
     * @param title the title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the content.
     * @return the result
     */
    public String getContent() {
        return content;
    }

    /**
     * Sets the content.
     * @param content the content
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Returns the sender uuid.
     * @return the result
     */
    public UUID getSenderUuid() {
        return senderUuid;
    }

    /**
     * Sets the sender uuid.
     * @param senderUuid the sender uuid
     */
    public void setSenderUuid(UUID senderUuid) {
        this.senderUuid = senderUuid;
    }

    /**
     * Returns the sender name.
     * @return the result
     */
    public String getSenderName() {
        return senderName;
    }

    /**
     * Sets the sender name.
     * @param senderName the sender name
     */
    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    /**
     * Returns the created at.
     * @return the result
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the created at.
     * @param createdAt the created at
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the expires at.
     * @return the result
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the expires at.
     * @param expiresAt the expires at
     */
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Returns whether active.
     * @return the result
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Sets the active.
     * @param active the active
     */
    public void setActive(boolean active) {
        isActive = active;
    }

    /**
     * Returns the priority.
     * @return the result
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Sets the priority.
     * @param priority the priority
     */
    public void setPriority(int priority) {
        this.priority = Math.max(Priority.MIN, Math.min(Priority.MAX, priority));
    }

    /**
     * Returns the target audience.
     * @return the result
     */
    public String getTargetAudience() {
        return targetAudience;
    }

    /**
     * Sets the target audience.
     * @param targetAudience the target audience
     */
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

    /** Returns a string representation of this object. */
    @Override
    public String toString() {
        return "BroadcastMessage{" +
                "id='" + id + '\'' +
                ", guildId='" + guildId + '\'' +
                ", messageType='" + messageType + '\'' +
                ", title='" + title + '\'' +
                ", senderName='" + senderName + '\'' +
                ", createdAt=" + createdAt +
                ", priority=" + priority +
                ", active=" + isActive +
                '}';
    }
}