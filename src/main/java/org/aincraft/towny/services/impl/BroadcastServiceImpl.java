package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.models.BroadcastMessage;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.BroadcastService;
import org.aincraft.towny.services.PermissionService;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implementation of BroadcastService with database operations
 */
@Singleton
public class BroadcastServiceImpl implements BroadcastService {

    private final DatabaseManager databaseManager;
    private final DataSource dataSource;
    private final Logger logger;
    private final TownService townService;
    private final ResidentService residentService;
    private final PermissionService permissionService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    public BroadcastServiceImpl(DatabaseManager databaseManager, Logger logger,
                               TownService townService, ResidentService residentService,
                               PermissionService permissionService) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
        this.townService = townService;
        this.residentService = residentService;
        this.permissionService = permissionService;
    }

    @Override
    public BroadcastMessage createBroadcast(String townId, String messageType, String title, String content,
                                          UUID senderUuid, String senderName) {
        BroadcastMessage broadcast = new BroadcastMessage(townId, messageType, title, content, senderUuid, senderName);

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO broadcast_messages (id, town_id, message_type, title, content, sender_uuid, sender_name, " +
                 "created_at, expires_at, is_active, priority, target_audience) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

            statement.setString(1, broadcast.getId());
            statement.setString(2, broadcast.getTownId());
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

            logger.info("Created new broadcast message: " + broadcast.getTitle() + " for town: " + townId);
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
    public List<BroadcastMessage> getActiveBroadcasts(String townId) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM broadcast_messages WHERE town_id = ? AND is_active = TRUE " +
                 "AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) " +
                 "ORDER BY priority DESC, created_at DESC")) {

            statement.setString(1, townId);

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
    public List<BroadcastMessage> getAllBroadcasts(String townId) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM broadcast_messages WHERE town_id = ? ORDER BY created_at DESC")) {

            statement.setString(1, townId);

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
    public List<BroadcastMessage> getBroadcastsByAudience(String townId, String audience) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM broadcast_messages WHERE town_id = ? AND target_audience = ? " +
                 "AND is_active = TRUE AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) " +
                 "ORDER BY priority DESC, created_at DESC")) {

            statement.setString(1, townId);
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
    public List<BroadcastMessage> getBroadcastsByType(String townId, String messageType) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM broadcast_messages WHERE town_id = ? AND message_type = ? " +
                 "AND is_active = TRUE AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) " +
                 "ORDER BY priority DESC, created_at DESC")) {

            statement.setString(1, townId);
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
    public List<BroadcastMessage> getBroadcastsForPlayer(String townId, UUID playerUuid, String playerRole) {
        List<BroadcastMessage> broadcasts = new ArrayList<>();

        // Get broadcasts for all audiences, then filter by role
        List<String> targetAudiences = Arrays.asList(BroadcastMessage.Audience.ALL, playerRole);

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT * FROM broadcast_messages WHERE town_id = ? AND target_audience IN (" +
                 String.join(",", Collections.nCopies(targetAudiences.size(), "?")) +
                 ") AND is_active = TRUE AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) " +
                 "ORDER BY priority DESC, created_at DESC")) {

            statement.setString(1, townId);
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
    public BroadcastMessage createWelcomeMessage(String townId, String newResidentName) {
        String title = "Welcome to " + getTownName(townId) + "!";
        String content = "Welcome " + newResidentName + " to our town! We're excited to have you as part of our community.";

        return createBroadcast(townId, BroadcastMessage.Type.WELCOME, title, content,
                              UUID.randomUUID(), "System");
    }

    @Override
    public BroadcastMessage createAlertMessage(String townId, String alertTitle, String alertContent,
                                             UUID senderUuid, String senderName, int priority) {
        BroadcastMessage alert = createBroadcast(townId, BroadcastMessage.Type.ALERT, alertTitle, alertContent,
                                               senderUuid, senderName);
        alert.setPriority(Math.max(1, Math.min(5, priority)));
        alert.setExpirationInHours(24); // Alerts expire after 24 hours by default

        updateBroadcast(alert);
        return alert;
    }

    @Override
    public BroadcastMessage createAnnouncement(String townId, String title, String content,
                                             UUID senderUuid, String senderName, int expirationDays) {
        BroadcastMessage announcement = createBroadcast(townId, BroadcastMessage.Type.ANNOUNCEMENT, title, content,
                                                       senderUuid, senderName);
        if (expirationDays > 0) {
            announcement.setExpirationInDays(expirationDays);
        }

        updateBroadcast(announcement);
        return announcement;
    }

    @Override
    public int cleanupExpiredBroadcasts(String townId) {
        int cleanedCount = 0;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE broadcast_messages SET is_active = FALSE " +
                 "WHERE is_active = TRUE AND expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP" +
                 (townId != null ? " AND town_id = ?" : ""))) {

            if (townId != null) {
                statement.setString(1, townId);
            }

            cleanedCount = statement.executeUpdate();
            if (cleanedCount > 0) {
                logger.info("Cleaned up " + cleanedCount + " expired broadcast messages" +
                           (townId != null ? " for town: " + townId : ""));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to cleanup expired broadcasts: " + e.getMessage(), e);
        }

        return cleanedCount;
    }

    @Override
    public BroadcastStatistics getBroadcastStatistics(String townId) {
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
                 "FROM broadcast_messages WHERE town_id = ? " +
                 "GROUP BY message_type ORDER BY COUNT(*) DESC LIMIT 1")) {

            statement.setString(1, townId);

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
    public boolean canCreateBroadcast(UUID playerUuid, String townId, String messageType) {
        // Check if player is in the town and has appropriate permissions
        Optional<Town> town = townService.getTownById(townId);
        if (town.isEmpty()) {
            return false;
        }

        // Mayor and assistants can create any type of broadcast
        if (town.get().getMayorUuid().equals(playerUuid) ||
            town.get().getAssistants().contains(playerUuid)) {
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
        Optional<Town> town = townService.getTownById(broadcast.getTownId());
        if (town.isEmpty()) {
            return 0;
        }

        int sentCount = 0;
        String formattedMessage = formatBroadcastMessage(broadcast);

        for (UUID residentUuid : town.get().getResidents()) {
            Player player = Bukkit.getPlayer(residentUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(formattedMessage);
                sentCount++;
            }
        }

        return sentCount;
    }

    private String formatBroadcastMessage(BroadcastMessage broadcast) {
        StringBuilder message = new StringBuilder();

        // Add header based on message type
        switch (broadcast.getMessageType()) {
            case BroadcastMessage.Type.ALERT:
                message.append("§c[§6ALERT§c] ");
                break;
            case BroadcastMessage.Type.ANNOUNCEMENT:
                message.append("§e[§6ANNOUNCEMENT§e] ");
                break;
            case BroadcastMessage.Type.WELCOME:
                message.append("§a[§bWELCOME§a] ");
                break;
            case BroadcastMessage.Type.WARNING:
                message.append("§c[§4WARNING§c] ");
                break;
            case BroadcastMessage.Type.CELEBRATION:
                message.append("§6[§eCELEBRATION§6] ");
                break;
            case BroadcastMessage.Type.ECONOMIC:
                message.append("§2[§aECONOMY§2] ");
                break;
            default:
                message.append("§7[§fBROADCAST§7] ");
        }

        message.append("§f").append(broadcast.getTitle()).append("\n");
        message.append("§7").append(broadcast.getContent()).append("\n");
        message.append("§8- ").append(broadcast.getSenderName()).append(" §8(")
               .append(broadcast.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, HH:mm")))
               .append("§8)");

        return message.toString();
    }

    private String getTownName(String townId) {
        Optional<Town> town = townService.getTownById(townId);
        return town.map(Town::getName).orElse("Unknown");
    }

    private BroadcastMessage mapResultSetToBroadcastMessage(ResultSet resultSet) throws SQLException {
        BroadcastMessage broadcast = new BroadcastMessage();
        broadcast.setId(resultSet.getString("id"));
        broadcast.setTownId(resultSet.getString("town_id"));
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