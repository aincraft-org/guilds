package org.aincraft.guilds.services.impl;



import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.BroadcastMessage;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.BroadcastService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implementation of BroadcastService with database operations
 */

public class BroadcastServiceImpl implements BroadcastService {

    private final DatabaseManager databaseManager;
    private final DataSource dataSource;
    private final Logger logger;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final PermissionService permissionService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


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

    @Override
    public BroadcastMessage createBroadcast(String guildId, String messageType, String title, String content,
                                          UUID senderUuid, String senderName) {
        BroadcastMessage broadcast = new BroadcastMessage(guildId, messageType, title, content, senderUuid, senderName);

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO broadcast_messages (id, guild_id, message_type, title, content, sender_uuid, sender_name, " +
                 "created_at, expires_at, is_active, priority, target_audience) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

            statement.setString(1, broadcast.getId());
            statement.setString(2, broadcast.getGuildId());
            statement.setString(3, broadcast.getMessageType());
            statement.setString(4, broadcast.getTitle());
            statement.setString(5, broadcast.getContent());
            statement.setString(6, broadcast.getSenderUuid().toString());
            statement.setString(7, broadcast.getSenderName());
            statement.setString(8, broadcast.getCreatedAt().format(DATE_FORMATTER));
            statement.setString(9, broadcast.getExpiresAt() != null ? broadcast.getExpiresAt().format(DATE_FORMATTER) : null);
            statement.setBoolean(10, broadcast.isActive());
            statement.setInt(11, broadcast.getPriority());
            statement.setString(12, broadcast.getTargetAudience());

            statement.executeUpdate();

            logger.info("Created new broadcast message: " + broadcast.getTitle() + " for guild: " + guildId);
            return broadcast;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create broadcast message: " + e.getMessage(), e);
            throw new RuntimeException("Failed to create broadcast message", e);
        }
    }

    @Override
    public Optional<BroadcastMessage> getBroadcast(String broadcastId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM broadcast_messages WHERE id = ?")) {

            statement.setString(1, broadcastId);

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

    @Override
    public List<BroadcastMessage> getActiveBroadcasts(String guildId) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM broadcast_messages WHERE guild_id = ? AND is_active = TRUE " +
                 "AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) " +
                 "ORDER BY priority DESC, created_at DESC")) {

            statement.setString(1, guildId);

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

    @Override
    public List<BroadcastMessage> getAllBroadcasts(String guildId) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM broadcast_messages WHERE guild_id = ? ORDER BY created_at DESC")) {

            statement.setString(1, guildId);

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

    @Override
    public List<BroadcastMessage> getBroadcastsByAudience(String guildId, String audience) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM broadcast_messages WHERE guild_id = ? AND target_audience = ? " +
                 "AND is_active = TRUE AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) " +
                 "ORDER BY priority DESC, created_at DESC")) {

            statement.setString(1, guildId);
            statement.setString(2, audience);

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

    @Override
    public List<BroadcastMessage> getBroadcastsByType(String guildId, String messageType) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM broadcast_messages WHERE guild_id = ? AND message_type = ? " +
                 "AND is_active = TRUE AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) " +
                 "ORDER BY priority DESC, created_at DESC")) {

            statement.setString(1, guildId);
            statement.setString(2, messageType);

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

    @Override
    public List<BroadcastMessage> getBroadcastsForPlayer(String guildId, UUID playerUuid, String playerRole) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        // Get broadcasts for all audiences, then filter by role
        List<String> targetAudiences = Arrays.asList(BroadcastMessage.Audience.ALL, playerRole);

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM broadcast_messages WHERE guild_id = ? AND target_audience IN (" +
                 String.join(",", Collections.nCopies(targetAudiences.size(), "?")) +
                 ") AND is_active = TRUE AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) " +
                 "ORDER BY priority DESC, created_at DESC")) {

            statement.setString(1, guildId);
            for (int i = 0; i < targetAudiences.size(); i++) {
                statement.setString(2 + i, targetAudiences.get(i));
            }

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

    @Override
    public boolean updateBroadcast(BroadcastMessage broadcast) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE broadcast_messages SET title = ?, content = ?, expires_at = ?, " +
                 "is_active = ?, priority = ?, target_audience = ? WHERE id = ?")) {

            statement.setString(1, broadcast.getTitle());
            statement.setString(2, broadcast.getContent());
            statement.setString(3, broadcast.getExpiresAt() != null ? broadcast.getExpiresAt().format(DATE_FORMATTER) : null);
            statement.setBoolean(4, broadcast.isActive());
            statement.setInt(5, broadcast.getPriority());
            statement.setString(6, broadcast.getTargetAudience());
            statement.setString(7, broadcast.getId());

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

    @Override
    public boolean archiveBroadcast(String broadcastId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE broadcast_messages SET is_active = FALSE WHERE id = ?")) {

            statement.setString(1, broadcastId);

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

    @Override
    public boolean deleteBroadcast(String broadcastId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM broadcast_messages WHERE id = ?")) {

            statement.setString(1, broadcastId);

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

    @Override
    public BroadcastMessage createWelcomeMessage(String guildId, String newResidentName) {
        String title = "Welcome to " + getGuildName(guildId) + "!";
        String content = "Welcome " + newResidentName + " to our guild! We're excited to have you as part of our community.";

        return createBroadcast(guildId, BroadcastMessage.Type.WELCOME, title, content,
                              UUID.randomUUID(), "System");
    }

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

    @Override
    public int cleanupExpiredBroadcasts(String guildId) {
        int cleanedCount = 0;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE broadcast_messages SET is_active = FALSE " +
                 "WHERE is_active = TRUE AND expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP" +
                 (guildId != null ? " AND guild_id = ?" : ""))) {

            if (guildId != null) {
                statement.setString(1, guildId);
            }

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

    @Override
    public BroadcastStatistics getBroadcastStatistics(String guildId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT COUNT(*) as total, " +
                 "SUM(CASE WHEN is_active = TRUE AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) THEN 1 ELSE 0 END) as active, " +
                 "SUM(CASE WHEN is_active = TRUE AND expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP THEN 1 ELSE 0 END) as expired, " +
                 "SUM(CASE WHEN message_type = 'announcement' THEN 1 ELSE 0 END) as announcements, " +
                 "SUM(CASE WHEN message_type = 'alert' THEN 1 ELSE 0 END) as alerts, " +
                 "SUM(CASE WHEN message_type = 'welcome' THEN 1 ELSE 0 END) as welcome, " +
                 "MAX(created_at) as last_broadcast, " +
                 "message_type as most_active_type " +
                 "FROM broadcast_messages WHERE guild_id = ? " +
                 "GROUP BY message_type ORDER BY COUNT(*) DESC LIMIT 1")) {

            statement.setString(1, guildId);

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

    @Override
    public int sendBroadcastToOnlineMembers(BroadcastMessage broadcast) {
        Optional<Guild> guild = guildService.getGuildById(broadcast.getGuildId());
        if (guild.isEmpty()) {
            return 0;
        }

        int sentCount = 0;
        String formattedMessage = org.aincraft.guilds.utils.BroadcastFormatter.format(broadcast);

        for (UUID residentUuid : guild.get().getResidents()) {
            Player player = Bukkit.getPlayer(residentUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(formattedMessage);
                sentCount++;
            }
        }

        return sentCount;
    }

    private String getGuildName(String guildId) {
        Optional<Guild> guild = guildService.getGuildById(guildId);
        return guild.map(Guild::getName).orElse("Unknown");
    }

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

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}