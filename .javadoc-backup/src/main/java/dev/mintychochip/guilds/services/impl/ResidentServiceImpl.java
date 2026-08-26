package dev.mintychochip.guilds.services.impl;



import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Resident;
import dev.mintychochip.sql.NamedSql;
import dev.mintychochip.sql.SqlParams;

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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of ResidentService with database operations
 */

public class ResidentServiceImpl implements dev.mintychochip.guilds.services.ResidentService {
    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();

    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The data source. */
    private final DataSource dataSource;
    /** The logger. */
    private final Logger logger;

    /** The date formatter constant. */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    /**
     * Creates a new resident service impl instance.
     * @param databaseManager the database manager
     * @param logger the logger
     */
    public ResidentServiceImpl(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
    }

    /**
     * Creates a new resident.
     * @param uuid the uuid
     * @param name the name
     * @return the result
     */
    @Override
    public Resident createResident(UUID uuid, String name) {
        long currentTime = System.currentTimeMillis();
        String joinedAt = LocalDateTime.now().format(DATE_FORMATTER);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "residents/insert.sql", Map.of(
                     "uuid", uuid.toString(),
                     "name", name,
                     "last_online", currentTime,
                     "is_online", false,
                     "joined_at", joinedAt,
                     "permissions_flags", 0))) {

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

    /**
     * Returns the resident.
     * @param uuid the uuid
     * @return the result
     */
    @Override
    public Optional<Resident> getResident(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "residents/select-by-uuid.sql", Map.of(
                     "uuid", uuid.toString()))) {

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

    /**
     * Returns the resident.
     * @param name the name
     * @return the result
     */
    @Override
    public Optional<Resident> getResident(String name) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "residents/select-by-name.sql", Map.of(
                     "name", name))) {

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

    /**
     * Updates the resident.
     * @param resident the resident
     * @return the result
     */
    @Override
    public Resident updateResident(Resident resident) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "residents/update.sql", SqlParams.of(
                     "name", resident.getName(),
                     "guild_name", resident.getGuild(),
                     "last_online", resident.getLastOnline(),
                     "is_online", resident.isOnline(),
                     "joined_at", resident.getJoinedAt().format(DATE_FORMATTER),
                     "permissions_flags", 0,
                     "uuid", resident.getUuid().toString()))) {

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

    /**
     * Deletes the resident.
     * @param uuid the uuid
     * @return the result
     */
    @Override
    public boolean deleteResident(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "residents/delete-by-uuid.sql", Map.of(
                     "uuid", uuid.toString()))) {

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

    /**
     * Returns the all residents.
     * @return the result
     */
    @Override
    public List<Resident> getAllResidents() {
        List<Resident> residents = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SQL.jdbc("residents/select-all.sql"))) {

            while (resultSet.next()) {
                residents.add(mapResultSetToResident(resultSet));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get all residents", e);
        }

        return residents;
    }

    /**
     * Returns the residents in guild.
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public List<Resident> getResidentsInGuild(String guildName) {
        List<Resident> residents = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "residents/select-by-guild.sql", Map.of(
                     "guild_name", guildName))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    residents.add(mapResultSetToResident(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get residents in guild: " + guildName, e);
        }

        return residents;
    }

    /**
     * Finds the residents.
     * @param prefix the prefix
     * @param limit the limit
     * @return the result
     */
    @Override
    public List<Resident> searchResidents(String prefix, int limit) {
        List<Resident> residents = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "residents/search.sql", Map.of(
                     "name_prefix", prefix + "%",
                     "limit", Math.max(1, limit)))) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    residents.add(mapResultSetToResident(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to search residents with prefix " + prefix, e);
        }

        return residents;
    }

    /**
     * Performs the resident exists operation.
     * @param uuid the uuid
     * @return the result
     */
    @Override
    public boolean residentExists(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "residents/count-by-uuid.sql", Map.of(
                     "uuid", uuid.toString()))) {

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

    /**
     * Returns the online residents count.
     * @return the result
     */
    @Override
    public int getOnlineResidentsCount() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SQL.jdbc("residents/count-online.sql"))) {

            if (resultSet.next()) {
                return resultSet.getInt(1);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get online residents count", e);
        }

        return 0;
    }

    /**
     * Updates the last online.
     * @param uuid the uuid
     */
    @Override
    public void updateLastOnline(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "residents/update-last-online.sql", Map.of(
                     "last_online", System.currentTimeMillis(),
                     "uuid", uuid.toString()))) {

            statement.executeUpdate();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update last online for resident: " + uuid, e);
        }
    }

    /**
     * Sets the online status.
     * @param uuid the uuid
     * @param online the online
     */
    @Override
    public void setOnlineStatus(UUID uuid, boolean online) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "residents/update-online-status.sql", Map.of(
                     "is_online", online,
                     "last_online", System.currentTimeMillis(),
                     "uuid", uuid.toString()))) {

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
