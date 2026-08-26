package dev.mintychochip.guilds.services.impl;



import dev.mintychochip.territory.model.GovernmentForm;
import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Alliance;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.AllianceService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.sql.NamedSql;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of AllianceService with database operations
 */

public class AllianceServiceImpl implements AllianceService {
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

    /** The date formatter constant. */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Cache alliances in memory for quick access
    /** The alliances by id. */
    private final Map<String, Alliance> alliancesById = new HashMap<>();
    /** The alliances by name. */
    private final Map<String, Alliance> alliancesByName = new HashMap<>();


    /**
     * Creates a new alliance service impl instance.
     * @param databaseManager the database manager
     * @param logger the logger
     * @param guildService the guild service
     */
    public AllianceServiceImpl(DatabaseManager databaseManager, Logger logger, GuildService guildService) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
        this.guildService = guildService;

        // Load all alliances from database on startup
        loadAllAlliances();
    }

    /** Loads the all alliances. */
    private void loadAllAlliances() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "alliances/select-all.sql", Map.of());
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                Alliance alliance = loadAllianceFromResult(resultSet);
                alliancesById.put(alliance.getId(), alliance);
                alliancesByName.put(alliance.getName().toLowerCase(), alliance);
            }

            logger.info("Loaded " + alliancesById.size() + " alliances from database");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load alliances from database", e);
        }
    }

    /**
     * Loads the alliance from result.
     * @param resultSet the result set
     * @return the result
     * @throws SQLException if an error occurs
     */
    private Alliance loadAllianceFromResult(ResultSet resultSet) throws SQLException {
        Alliance alliance = new Alliance();

        alliance.setId(resultSet.getString("id"));
        alliance.setName(resultSet.getString("name"));
        alliance.setCapitalGuildId(resultSet.getString("capital_guild_id"));
        alliance.setKingUuid(UUID.fromString(resultSet.getString("king_uuid")));
        alliance.setTaxRate(resultSet.getDouble("tax_rate"));
        alliance.setOpen(resultSet.getBoolean("is_open"));

        String createdAtStr = resultSet.getString("created_at");
        if (createdAtStr != null) {
            alliance.setCreatedAt(LocalDateTime.parse(createdAtStr, DATE_FORMATTER));
        }

        // Load member guilds
        String memberGuildsStr = resultSet.getString("member_guilds");
        if (memberGuildsStr != null) {
            alliance.setMemberGuildIds(new HashSet<>(Arrays.asList(memberGuildsStr.split(","))));
        }

        // Load ministers
        String ministersStr = resultSet.getString("ministers");
        if (ministersStr != null) {
            Set<UUID> ministers = new HashSet<>();
            for (String minister : ministersStr.split(",")) {
                ministers.add(UUID.fromString(minister));
            }
            alliance.setMinisters(ministers);
        }

        // Load alliances and enemies
        String relationsStr = resultSet.getString("relations");
        String relationTypesStr = resultSet.getString("relation_types");

        if (relationsStr != null && relationTypesStr != null) {
            String[] relations = relationsStr.split(",");
            String[] relationTypes = relationTypesStr.split(",");

            for (int i = 0; i < Math.min(relations.length, relationTypes.length); i++) {
                String allianceName = relations[i];
                String relationType = relationTypes[i];

                if ("ALLY".equals(relationType)) {
                    alliance.addAlly(allianceName);
                } else if ("ENEMY".equals(relationType)) {
                    alliance.addEnemy(allianceName);
                }
            }
        }

        return alliance;
    }

    /**
     * Returns the alliance.
     * @param name the name
     * @return the result
     */
    @Override
    public Optional<Alliance> getAlliance(String name) {
        return Optional.ofNullable(alliancesByName.get(name.toLowerCase()));
    }

    /**
     * Returns the alliance by id.
     * @param id the id
     * @return the result
     */
    @Override
    public Optional<Alliance> getAllianceById(String id) {
        return Optional.ofNullable(alliancesById.get(id));
    }

    /**
     * Returns the all alliances.
     * @return the result
     */
    @Override
    public List<Alliance> getAllAlliances() {
        return new ArrayList<>(alliancesById.values());
    }

    /**
     * Creates a new alliance.
     * @param name the name
     * @param capitalGuild the capital guild
     * @param kingUuid the king uuid
     */
    @Override
    public void createAlliance(String name, Guild capitalGuild, UUID kingUuid) {
        if (getAlliance(name).isPresent()) {
            throw new IllegalArgumentException("Alliance already exists: " + name);
        }
        Alliance alliance = new Alliance(name, capitalGuild.getId(), kingUuid);

        databaseManager.executeTransaction(connection -> {

            // Insert alliance
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/insert.sql", Map.of(
                    "id", alliance.getId(),
                    "name", alliance.getName(),
                    "capital_guild_id", alliance.getCapitalGuildId(),
                    "king_uuid", kingUuid.toString(),
                    "tax_rate", alliance.getTaxRate(),
                    "is_open", alliance.isOpen(),
                    "created_at", alliance.getCreatedAt().format(DATE_FORMATTER)))) {
                statement.executeUpdate();
            }

            // Add capital guild as member
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/insert-member.sql", Map.of(
                    "alliance_id", alliance.getId(),
                    "guild_id", alliance.getCapitalGuildId()))) {
                statement.executeUpdate();
            }
        });

        // Update cache
        alliancesById.put(alliance.getId(), alliance);
        alliancesByName.put(alliance.getName().toLowerCase(), alliance);

        logger.info("Created alliance: " + name);
    }

    /**
     * Deletes the alliance.
     * @param name the name
     */
    @Override
    public void deleteAlliance(String name) {
        Optional<Alliance> allianceOpt = getAlliance(name);
        if (allianceOpt.isEmpty()) {
            return;
        }

        Alliance alliance = allianceOpt.get();

        databaseManager.executeTransaction(connection -> {
            // Delete relations first
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/delete-relations.sql", Map.of(
                    "alliance_id", alliance.getId(),
                    "other_alliance", alliance.getName()))) {
                statement.executeUpdate();
            }

            // Delete ministers
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/delete-ministers.sql", Map.of(
                    "alliance_id", alliance.getId()))) {
                statement.executeUpdate();
            }

            // Delete members
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/delete-members.sql", Map.of(
                    "alliance_id", alliance.getId()))) {
                statement.executeUpdate();
            }

            // Delete alliance
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/delete-by-id.sql", Map.of(
                    "id", alliance.getId()))) {
                statement.executeUpdate();
            }
        });

        // Update cache
        alliancesById.remove(alliance.getId());
        alliancesByName.remove(alliance.getName().toLowerCase());

        logger.info("Deleted alliance: " + name);
    }

    /**
     * Returns the governance form.
     * @param allianceId the alliance id
     * @return the result
     */
    @Override
    public GovernmentForm getGovernanceForm(String allianceId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "alliances/select-governance-form.sql", Map.of(
                     "id", allianceId))) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return GovernmentForm.fromString(resultSet.getString("governance_form"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to read governance form for alliance " + allianceId, e);
        }
        return GovernmentForm.MONARCHY;
    }

    /**
     * Adds the guild.
     * @param alliance the alliance
     * @param guildId the guild id
     */
    @Override
    public void addGuild(Alliance alliance, String guildId) {
        databaseManager.executeTransaction(connection -> {
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/insert-member.sql", Map.of(
                    "alliance_id", alliance.getId(),
                    "guild_id", guildId))) {
                statement.executeUpdate();
            }
        });

        alliance.addGuild(guildId);
    }

    /**
     * Removes the guild.
     * @param alliance the alliance
     * @param guildId the guild id
     */
    @Override
    public void removeGuild(Alliance alliance, String guildId) {
        if (alliance.getCapitalGuildId().equals(guildId)) {
            throw new IllegalArgumentException("Cannot remove capital guild from alliance");
        }

        databaseManager.executeTransaction(connection -> {
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/delete-member.sql", Map.of(
                    "alliance_id", alliance.getId(),
                    "guild_id", guildId))) {
                statement.executeUpdate();
            }
        });

        alliance.removeGuild(guildId);
    }

    /**
     * Sets the king.
     * @param alliance the alliance
     * @param newKing the new king
     */
    @Override
    public void setKing(Alliance alliance, UUID newKing) {
        databaseManager.executeTransaction(connection -> {
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/update-king.sql", Map.of(
                    "king_uuid", newKing.toString(),
                    "id", alliance.getId()))) {
                statement.executeUpdate();
            }
        });

        alliance.setKingUuid(newKing);
    }

    /**
     * Adds the minister.
     * @param alliance the alliance
     * @param minister the minister
     */
    @Override
    public void addMinister(Alliance alliance, UUID minister) {
        databaseManager.executeTransaction(connection -> {
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/insert-minister.sql", Map.of(
                    "alliance_id", alliance.getId(),
                    "player_uuid", minister.toString()))) {
                statement.executeUpdate();
            }
        });

        alliance.addMinister(minister);
    }

    /**
     * Removes the minister.
     * @param alliance the alliance
     * @param minister the minister
     */
    @Override
    public void removeMinister(Alliance alliance, UUID minister) {
        databaseManager.executeTransaction(connection -> {
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/delete-minister.sql", Map.of(
                    "alliance_id", alliance.getId(),
                    "player_uuid", minister.toString()))) {
                statement.executeUpdate();
            }
        });

        alliance.removeMinister(minister);
    }

    /**
     * Updates the alliance relation.
     * @param alliance the alliance
     * @param otherAlliance the other alliance
     * @param relationType the relation type
     * @param isAdding the is adding
     */
    private void updateAllianceRelation(Alliance alliance, String otherAlliance, String relationType, boolean isAdding) {
        databaseManager.executeTransaction(connection -> {
            if (isAdding) {
                try (PreparedStatement statement = SQL.prepare(connection, "alliances/insert-relation.sql", Map.of(
                        "alliance_id", alliance.getId(),
                        "other_alliance", otherAlliance,
                        "relation_type", relationType))) {
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = SQL.prepare(connection, "alliances/delete-relation.sql", Map.of(
                        "alliance_id", alliance.getId(),
                        "other_alliance", otherAlliance,
                        "relation_type", relationType))) {
                    statement.executeUpdate();
                }
            }
        });
    }

    /**
     * Adds the ally.
     * @param alliance the alliance
     * @param otherAlliance the other alliance
     */
    @Override
    public void addAlly(Alliance alliance, String otherAlliance) {
        // Remove as enemy if exists
        alliance.removeEnemy(otherAlliance);
        updateAllianceRelation(alliance, otherAlliance, "ENEMY", false);

        // Add as ally
        alliance.addAlly(otherAlliance);
        updateAllianceRelation(alliance, otherAlliance, "ALLY", true);
    }

    /**
     * Removes the ally.
     * @param alliance the alliance
     * @param otherAlliance the other alliance
     */
    @Override
    public void removeAlly(Alliance alliance, String otherAlliance) {
        alliance.removeAlly(otherAlliance);
        updateAllianceRelation(alliance, otherAlliance, "ALLY", false);
    }

    /**
     * Adds the enemy.
     * @param alliance the alliance
     * @param otherAlliance the other alliance
     */
    @Override
    public void addEnemy(Alliance alliance, String otherAlliance) {
        // Remove as ally if exists
        alliance.removeAlly(otherAlliance);
        updateAllianceRelation(alliance, otherAlliance, "ALLY", false);

        // Add as enemy
        alliance.addEnemy(otherAlliance);
        updateAllianceRelation(alliance, otherAlliance, "ENEMY", true);
    }

    /**
     * Removes the enemy.
     * @param alliance the alliance
     * @param otherAlliance the other alliance
     */
    @Override
    public void removeEnemy(Alliance alliance, String otherAlliance) {
        alliance.removeEnemy(otherAlliance);
        updateAllianceRelation(alliance, otherAlliance, "ENEMY", false);
    }

    /**
     * Sets the tax rate.
     * @param alliance the alliance
     * @param rate the rate
     */
    @Override
    public void setTaxRate(Alliance alliance, double rate) {
        databaseManager.executeTransaction(connection -> {
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/update-tax-rate.sql", Map.of(
                    "tax_rate", rate,
                    "id", alliance.getId()))) {
                statement.executeUpdate();
            }
        });

        alliance.setTaxRate(rate);
    }

    /**
     * Sets the open.
     * @param alliance the alliance
     * @param open the open
     */
    @Override
    public void setOpen(Alliance alliance, boolean open) {
        databaseManager.executeTransaction(connection -> {
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/update-open.sql", Map.of(
                    "is_open", open,
                    "id", alliance.getId()))) {
                statement.executeUpdate();
            }
        });

        alliance.setOpen(open);
    }

    /**
     * Updates the alliance.
     * @param alliance the alliance
     */
    @Override
    public void updateAlliance(Alliance alliance) {
        databaseManager.executeTransaction(connection -> {
            try (PreparedStatement statement = SQL.prepare(connection, "alliances/update.sql", Map.of(
                    "name", alliance.getName(),
                    "capital_guild_id", alliance.getCapitalGuildId(),
                    "king_uuid", alliance.getKingUuid().toString(),
                    "tax_rate", alliance.getTaxRate(),
                    "is_open", alliance.isOpen(),
                    "id", alliance.getId()))) {
                statement.executeUpdate();
            }
        });
    }

    /**
     * Saves the alliance.
     * @param alliance the alliance
     */
    @Override
    public void saveAlliance(Alliance alliance) {
        // Update cache
        alliancesById.put(alliance.getId(), alliance);
        alliancesByName.put(alliance.getName().toLowerCase(), alliance);

        // Update database
        updateAlliance(alliance);
    }
}
