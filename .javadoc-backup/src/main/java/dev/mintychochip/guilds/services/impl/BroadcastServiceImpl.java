package dev.mintychochip.guilds.services.impl;



import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.BroadcastMessage;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.BroadcastService;
import dev.mintychochip.guilds.services.PermissionService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.sql.NamedSql;
import dev.mintychochip.sql.SqlParams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of BroadcastService with database operations
 */

public class BroadcastServiceImpl implements BroadcastService {
    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();

    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The data source. */
    private final DataSource dataSource;
    /** The logger. */
    private final Logger logger;
    /** The guild service. */
    private final GuildService guildService;
    /** The resident service. */
    private final ResidentService residentService;
    /** The permission service. */
    private final PermissionService permissionService;

    /** The date formatter constant. */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    /**
     * Creates a new broadcast service impl instance.
     * @param databaseManager the database manager
     * @param logger the logger
     * @param guildService the guild service
     * @param residentService the resident service
     * @param permissionService the permission service
     */
    public BroadcastServiceImpl(DatabaseManager databaseManager, Logger logger,
                               GuildService guildService, ResidentService residentService,
                               PermissionService permissionService) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
        this.guildService = guildService;
        this.residentService = residentService;
        this.permissionService = permissionService;
    }

    /**
     * Creates a new broadcast.
     * @param guildId the guild id
     * @param messageType the message type
     * @param title the title
     * @param content the content
     * @param senderUuid the sender uuid
     * @param senderName the sender name
     * @return the result
     */
    @Override
    public BroadcastMessage createBroadcast(String guildId, String messageType, String title, String content,
                                          UUID senderUuid, String senderName) {
        BroadcastMessage broadcast = new BroadcastMessage(guildId, messageType, title, content, senderUuid, senderName);

        try (Connection connection = getConnection();
             PreparedStatement statement = SQL.prepare(connection, "broadcasts/insert.sql", SqlParams.of(
                     "id", broadcast.getId(),
                     "guild_id", broadcast.getGuildId(),
                     "message_type", broadcast.getMessageType(),
                     "title", broadcast.getTitle(),
                     "content", broadcast.getContent(),
                     "sender_uuid", broadcast.getSenderUuid().toString(),
                     "sender_name", broadcast.getSenderName(),
                     "created_at", broadcast.getCreatedAt().format(DATE_FORMATTER),
                     "expires_at", broadcast.getExpiresAt() != null ? broadcast.getExpiresAt().format(DATE_FORMATTER) : null,
                     "is_active", broadcast.isActive(),
                     "priority", broadcast.getPriority(),
                     "target_audience", broadcast.getTargetAudience()))) {

            statement.executeUpdate();

            logger.info("Created new broadcast message: " + broadcast.getTitle() + " for guild: " + guildId);
            return broadcast;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create broadcast message: " + e.getMessage(), e);
            throw new RuntimeException("Failed to create broadcast message", e);
        }
    }

    /**
     * Returns the broadcast.
     * @param broadcastId the broadcast id
     * @return the result
     */
    @Override
    public Optional<BroadcastMessage> getBroadcast(String broadcastId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = SQL.prepare(connection, "broadcasts/select-by-id.sql", Map.of(
                     "id", broadcastId))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToBroadcastMessage(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get broadcast message: " + e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * Returns the active broadcasts.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public List<BroadcastMessage> getActiveBroadcasts(String guildId) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = SQL.prepare(connection, "broadcasts/select-active.sql", Map.of(
                     "guild_id", guildId))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    broadcasts.add(mapResultSetToBroadcastMessage(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get active broadcasts: " + e.getMessage(), e);
        }

        return broadcasts;
    }

    /**
     * Returns the all broadcasts.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public List<BroadcastMessage> getAllBroadcasts(String guildId) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = SQL.prepare(connection, "broadcasts/select-all.sql", Map.of(
                     "guild_id", guildId))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    broadcasts.add(mapResultSetToBroadcastMessage(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get all broadcasts: " + e.getMessage(), e);
        }

        return broadcasts;
    }

    /**
     * Returns the broadcasts by audience.
     * @param guildId the guild id
     * @param audience the audience
     * @return the result
     */
    @Override
    public List<BroadcastMessage> getBroadcastsByAudience(String guildId, String audience) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = SQL.prepare(connection, "broadcasts/select-by-audience.sql", Map.of(
                     "guild_id", guildId,
                     "audience", audience))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    broadcasts.add(mapResultSetToBroadcastMessage(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get broadcasts by audience: " + e.getMessage(), e);
        }

        return broadcasts;
    }

    /**
     * Returns the broadcasts by type.
     * @param guildId the guild id
     * @param messageType the message type
     * @return the result
     */
    @Override
    public List<BroadcastMessage> getBroadcastsByType(String guildId, String messageType) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = SQL.prepare(connection, "broadcasts/select-by-type.sql", Map.of(
                     "guild_id", guildId,
                     "message_type", messageType))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    broadcasts.add(mapResultSetToBroadcastMessage(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get broadcasts by type: " + e.getMessage(), e);
        }

        return broadcasts;
    }

    /**
     * Returns the broadcasts for player.
     * @param guildId the guild id
     * @param playerUuid the player uuid
     * @param playerRole the player role
     * @return the result
     */
    @Override
    public List<BroadcastMessage> getBroadcastsForPlayer(String guildId, UUID playerUuid, String playerRole) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        List<String> targetAudiences = List.of(BroadcastMessage.Audience.ALL, playerRole);

        try (Connection connection = getConnection();
             PreparedStatement statement = SQL.prepare(connection, "broadcasts/select-for-player.sql", Map.of(
                     "guild_id", guildId,
                     "audiences", targetAudiences))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    broadcasts.add(mapResultSetToBroadcastMessage(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get broadcasts for player: " + e.getMessage(), e);
        }

        return broadcasts;
    }

    /**
     * Updates the broadcast.
     * @param broadcast the broadcast
     * @return the result
     */
    @Override
    public boolean updateBroadcast(BroadcastMessage broadcast) {
        try (Connection connection = getConnection();
             PreparedStatement statement = SQL.prepare(connection, "broadcasts/update.sql", SqlParams.of(
                     "title", broadcast.getTitle(),
                     "content", broadcast.getContent(),
                     "expires_at", broadcast.getExpiresAt() != null ? broadcast.getExpiresAt().format(DATE_FORMATTER) : null,
                     "is_active", broadcast.isActive(),
                     "priority", broadcast.getPriority(),
                     "target_audience", broadcast.getTargetAudience(),
                     "id", broadcast.getId()))) {

            int updatedRows = statement.executeUpdate();
            if (updatedRows > 0) {
                logger.info("Updated broadcast message: " + broadcast.getId());
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update broadcast message: " + e.getMessage(), e);
        }

        return false;
    }

    /**
     * Performs the archive broadcast operation.
     * @param broadcastId the broadcast id
     * @return the result
     */
    @Override
    public boolean archiveBroadcast(String broadcastId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = SQL.prepare(connection, "broadcasts/archive.sql", Map.of(
                     "id", broadcastId))) {

            int updatedRows = statement.executeUpdate();
            if (updatedRows > 0) {
                logger.info("Archived broadcast message: " + broadcastId);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to archive broadcast message: " + e.getMessage(), e);
        }

        return false;
    }

    /**
     * Deletes the broadcast.
     * @param broadcastId the broadcast id
     * @return the result
     */
    @Override
    public boolean deleteBroadcast(String broadcastId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = SQL.prepare(connection, "broadcasts/delete.sql", Map.of(
                     "id", broadcastId))) {

            int deletedRows = statement.executeUpdate();
            if (deletedRows > 0) {
                logger.info("Deleted broadcast message: " + broadcastId);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete broadcast message: " + e.getMessage(), e);
        }

        return false;
    }

    /**
     * Creates a new welcome message.
     * @param guildId the guild id
     * @param newResidentName the new resident name
     * @return the result
     */
    @Override
    public BroadcastMessage createWelcomeMessage(String guildId, String newResidentName) {
        String title = "Welcome to " + getGuildName(guildId) + "!";
        String content = "Welcome " + newResidentName + " to our guild! We're excited to have you as part of our community.";

        return createBroadcast(guildId, BroadcastMessage.Type.WELCOME, title, content,
                              UUID.randomUUID(), "System");
    }

    /**
     * Creates a new alert message.
     * @param guildId the guild id
     * @param alertTitle the alert title
     * @param alertContent the alert content
     * @param senderUuid the sender uuid
     * @param senderName the sender name
     * @param priority the priority
     * @return the result
     */
    @Override
    public BroadcastMessage createAlertMessage(String guildId, String alertTitle, String alertContent,
                                             UUID senderUuid, String senderName, int priority) {
        BroadcastMessage alert = createBroadcast(guildId, BroadcastMessage.Type.ALERT, alertTitle, alertContent,
                                               senderUuid, senderName);
        alert.setPriority(Math.max(1, Math.min(5, priority)));
        alert.setExpirationInHours(24); // Alerts expire after 24 hours by default

        updateBroadcast(alert);
        return alert;
    }

    /**
     * Creates a new announcement.
     * @param guildId the guild id
     * @param title the title
     * @param content the content
     * @param senderUuid the sender uuid
     * @param senderName the sender name
     * @param expirationDays the expiration days
     * @return the result
     */
    @Override
    public BroadcastMessage createAnnouncement(String guildId, String title, String content,
                                             UUID senderUuid, String senderName, int expirationDays) {
        BroadcastMessage announcement = createBroadcast(guildId, BroadcastMessage.Type.ANNOUNCEMENT, title, content,
                                                       senderUuid, senderName);
        if (expirationDays > 0) {
            announcement.setExpirationInDays(expirationDays);
        }

        updateBroadcast(announcement);
        return announcement;
    }

    /**
     * Performs the cleanup expired broadcasts operation.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public int cleanupExpiredBroadcasts(String guildId) {
        int cleanedCount = 0;

        try (Connection connection = getConnection();
             PreparedStatement statement = guildId != null
                     ? SQL.prepare(connection, "broadcasts/cleanup-expired-for-guild.sql", Map.of("guild_id", guildId))
                     : SQL.prepare(connection, "broadcasts/cleanup-expired.sql", Map.of())) {

            cleanedCount = statement.executeUpdate();
            if (cleanedCount > 0) {
                logger.info("Cleaned up " + cleanedCount + " expired broadcast messages" +
                           (guildId != null ? " for guild: " + guildId : ""));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to cleanup expired broadcasts: " + e.getMessage(), e);
        }

        return cleanedCount;
    }

    /**
     * Returns the broadcast statistics.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public BroadcastStatistics getBroadcastStatistics(String guildId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = SQL.prepare(connection, "broadcasts/select-statistics.sql", Map.of(
                     "guild_id", guildId))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new BroadcastStatistics(
                        resultSet.getInt("total"),
                        resultSet.getInt("active"),
                        resultSet.getInt("expired"),
                        resultSet.getInt("announcements"),
                        resultSet.getInt("alerts"),
                        resultSet.getInt("welcome"),
                        resultSet.getTimestamp("last_broadcast") != null ?
                            resultSet.getTimestamp("last_broadcast").toLocalDateTime() : null,
                        resultSet.getString("most_active_type")
                    );
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get broadcast statistics: " + e.getMessage(), e);
        }

        return new BroadcastStatistics(0, 0, 0, 0, 0, 0, null, null);
    }

    /**
     * Returns whether create broadcast.
     * @param playerUuid the player uuid
     * @param guildId the guild id
     * @param messageType the message type
     * @return the result
     */
    @Override
    public boolean canCreateBroadcast(UUID playerUuid, String guildId, String messageType) {
        // Check if player is in the guild and has appropriate permissions
        Optional<Guild> guild = guildService.getGuildById(guildId);
        if (guild.isEmpty()) {
            return false;
        }

        // Mayor and assistants can create any type of broadcast
        if (guild.get().getMayorUuid().equals(playerUuid) ||
            guild.get().getAssistants().contains(playerUuid)) {
            return true;
        }

        // Regular residents can only create certain types
        if (messageType.equals(BroadcastMessage.Type.WELCOME) ||
            messageType.equals(BroadcastMessage.Type.CELEBRATION)) {
            return true;
        }

        return false;
    }

    /**
     * Performs the send broadcast to online members operation.
     * @param broadcast the broadcast
     * @return the result
     */
    @Override
    public int sendBroadcastToOnlineMembers(BroadcastMessage broadcast) {
        Optional<Guild> guild = guildService.getGuildById(broadcast.getGuildId());
        if (guild.isEmpty()) {
            return 0;
        }

        int sentCount = 0;
        String formattedMessage = dev.mintychochip.guilds.utils.BroadcastFormatter.format(broadcast);

        for (UUID residentUuid : guild.get().getResidents()) {
            Player player = Bukkit.getPlayer(residentUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(formattedMessage);
                sentCount++;
            }
        }

        return sentCount;
    }

    /**
     * Returns the guild name.
     * @param guildId the guild id
     * @return the result
     */
    private String getGuildName(String guildId) {
        Optional<Guild> guild = guildService.getGuildById(guildId);
        return guild.map(Guild::getName).orElse("Unknown");
    }

    /**
     * Performs the map result set to broadcast message operation.
     * @param resultSet the result set
     * @return the result
     * @throws SQLException if an error occurs
     */
    private BroadcastMessage mapResultSetToBroadcastMessage(ResultSet resultSet) throws SQLException {
        BroadcastMessage broadcast = new BroadcastMessage();
        broadcast.setId(resultSet.getString("id"));
        broadcast.setGuildId(resultSet.getString("guild_id"));
        broadcast.setMessageType(resultSet.getString("message_type"));
        broadcast.setTitle(resultSet.getString("title"));
        broadcast.setContent(resultSet.getString("content"));
        broadcast.setSenderUuid(UUID.fromString(resultSet.getString("sender_uuid")));
        broadcast.setSenderName(resultSet.getString("sender_name"));
        broadcast.setCreatedAt(LocalDateTime.parse(resultSet.getString("created_at"), DATE_FORMATTER));

        String expiresAt = resultSet.getString("expires_at");
        if (expiresAt != null) {
            broadcast.setExpiresAt(LocalDateTime.parse(expiresAt, DATE_FORMATTER));
        }

        broadcast.setActive(resultSet.getBoolean("is_active"));
        broadcast.setPriority(resultSet.getInt("priority"));
        broadcast.setTargetAudience(resultSet.getString("target_audience"));

        return broadcast;
    }

    /**
     * Returns the connection.
     * @return the result
     * @throws SQLException if an error occurs
     */
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}