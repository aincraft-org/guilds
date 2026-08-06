package org.aincraft.guilds.services.impl;



import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Resident;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of ResidentService with database operations
 */

public class ResidentServiceImpl implements org.aincraft.guilds.services.ResidentService {

    private final DatabaseManager databaseManager;
    private final DataSource dataSource;
    private final Logger logger;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    public ResidentServiceImpl(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
    }

    @Override
    public Resident createResident(UUID uuid, String name) {
        String sql = "INSERT INTO residents (uuid, name, last_online, is_online, joined_at, permissions_flags) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            long currentTime = System.currentTimeMillis();
            String joinedAt = LocalDateTime.now().format(DATE_FORMATTER);

            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setLong(3, currentTime);
            statement.setBoolean(4, false);
            statement.setString(5, joinedAt);
            statement.setInt(6, 0); // Default permission flags

            statement.executeUpdate();

            Resident resident = new Resident(uuid, name);
            resident.setLastOnline(currentTime);
            resident.setOnline(false);
            resident.setJoinedAt(LocalDateTime.now());

            logger.info("Created new resident: " + name + " (" + uuid + ")");
            return resident;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create resident: " + name, e);
            throw new RuntimeException("Failed to create resident", e);
        }
    }

    @Override
    public Optional<Resident> getResident(UUID uuid) {
        String sql = "SELECT uuid, name, guild_name, last_online, is_online, joined_at, permissions_flags FROM residents WHERE uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToResident(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get resident: " + uuid, e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Resident> getResident(String name) {
        String sql = "SELECT uuid, name, guild_name, last_online, is_online, joined_at, permissions_flags FROM residents WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToResident(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get resident: " + name, e);
        }

        return Optional.empty();
    }

    @Override
    public Resident updateResident(Resident resident) {
        String sql = "UPDATE residents SET name = ?, guild_name = ?, last_online = ?, is_online = ?, joined_at = ?, permissions_flags = ? WHERE uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, resident.getName());
            statement.setString(2, resident.getGuild());
            statement.setLong(3, resident.getLastOnline());
            statement.setBoolean(4, resident.isOnline());
            statement.setString(5, resident.getJoinedAt().format(DATE_FORMATTER));
            statement.setInt(6, 0); // Will be updated with permission flags later
            statement.setString(7, resident.getUuid().toString());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Updated resident: " + resident.getName());
            }

            return resident;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update resident: " + resident.getName(), e);
            throw new RuntimeException("Failed to update resident", e);
        }
    }

    @Override
    public boolean deleteResident(UUID uuid) {
        String sql = "DELETE FROM residents WHERE uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());

            int rowsDeleted = statement.executeUpdate();

            if (rowsDeleted > 0) {
                logger.info("Deleted resident: " + uuid);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete resident: " + uuid, e);
        }

        return false;
    }

    @Override
    public List<Resident> getAllResidents() {
        String sql = "SELECT uuid, name, guild_name, last_online, is_online, joined_at, permissions_flags FROM residents ORDER BY name";
        List<Resident> residents = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                residents.add(mapResultSetToResident(resultSet));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get all residents", e);
        }

        return residents;
    }

    @Override
    public List<Resident> getResidentsInGuild(String guildName) {
        String sql = "SELECT uuid, name, guild_name, last_online, is_online, joined_at, permissions_flags FROM residents WHERE guild_name = ? ORDER BY name";
        List<Resident> residents = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildName);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    residents.add(mapResultSetToResident(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get residents in town: " + guildName, e);
        }

        return residents;
    }

    @Override
    public boolean residentExists(UUID uuid) {
        String sql = "SELECT COUNT(*) FROM residents WHERE uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check if resident exists: " + uuid, e);
        }

        return false;
    }

    @Override
    public int getOnlineResidentsCount() {
        String sql = "SELECT COUNT(*) FROM residents WHERE is_online = TRUE";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            if (resultSet.next()) {
                return resultSet.getInt(1);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get online residents count", e);
        }

        return 0;
    }

    @Override
    public void updateLastOnline(UUID uuid) {
        String sql = "UPDATE residents SET last_online = ? WHERE uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            long currentTime = System.currentTimeMillis();
            statement.setLong(1, currentTime);
            statement.setString(2, uuid.toString());

            statement.executeUpdate();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update last online for resident: " + uuid, e);
        }
    }

    @Override
    public void setOnlineStatus(UUID uuid, boolean online) {
        String sql = "UPDATE residents SET is_online = ?, last_online = ? WHERE uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            long currentTime = System.currentTimeMillis();
            statement.setBoolean(1, online);
            statement.setLong(2, currentTime);
            statement.setString(3, uuid.toString());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.fine("Set online status for resident " + uuid + ": " + online);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to set online status for resident: " + uuid, e);
        }
    }

    /**
     * Map a ResultSet to a Resident object
     */
    private Resident mapResultSetToResident(ResultSet resultSet) throws SQLException {
        UUID uuid = UUID.fromString(resultSet.getString("uuid"));
        String name = resultSet.getString("name");
        String guild = resultSet.getString("guild_name");
        long lastOnline = resultSet.getLong("last_online");
        boolean isOnline = resultSet.getBoolean("is_online");
        String joinedAtStr = resultSet.getString("joined_at");

        Resident resident = new Resident(uuid, name);
        resident.setGuild(guild);
        resident.setLastOnline(lastOnline);
        resident.setOnline(isOnline);

        if (joinedAtStr != null) {
            resident.setJoinedAt(LocalDateTime.parse(joinedAtStr, DATE_FORMATTER));
        }

        return resident;
    }
}