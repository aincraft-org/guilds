package org.aincraft.guilds.services.impl;



import com.azoth.territory.model.GovernmentForm;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Alliance;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.AllianceService;
import org.aincraft.guilds.services.GuildService;

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

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final DataSource dataSource;
    private final Logger logger;
    private final GuildService guildService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Cache alliances in memory for quick access
    private final Map<String, Alliance> alliancesById = new HashMap<>();
    private final Map<String, Alliance> alliancesByName = new HashMap<>();


    public AllianceServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager, Logger logger, GuildService guildService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
        this.guildService = guildService;

        // Load all alliances from database on startup
        loadAllAlliances();
    }

    private void loadAllAlliances() {
        String sql = """
            SELECT n.id, n.name, n.capital_guild_id, n.king_uuid, n.tax_rate, n.is_open, n.created_at,
                   STRING_AGG(DISTINCT nm.guild_id, ',') as member_guilds,
                   STRING_AGG(DISTINCT nmin.player_uuid, ',') as ministers,
                   STRING_AGG(DISTINCT nr.other_alliance, ',') as relations,
                   STRING_AGG(DISTINCT nr.relation_type, ',') as relation_types
            FROM alliances n
            LEFT JOIN alliance_members nm ON n.id = nm.alliance_id
            LEFT JOIN alliance_ministers nmin ON n.id = nmin.alliance_id
            LEFT JOIN alliance_relations nr ON n.id = nr.alliance_id
            GROUP BY n.id, n.name, n.capital_guild_id, n.king_uuid, n.tax_rate, n.is_open, n.created_at
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
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

    @Override
    public Optional<Alliance> getAlliance(String name) {
        return Optional.ofNullable(alliancesByName.get(name.toLowerCase()));
    }

    @Override
    public Optional<Alliance> getAllianceById(String id) {
        return Optional.ofNullable(alliancesById.get(id));
    }

    @Override
    public List<Alliance> getAllAlliances() {
        return new ArrayList<>(alliancesById.values());
    }

    @Override
    public void createAlliance(String name, Guild capitalGuild, UUID kingUuid) {
        if (getAlliance(name).isPresent()) {
            throw new IllegalArgumentException("Alliance already exists: " + name);
        }
        Alliance alliance = new Alliance(name, capitalGuild.getId(), kingUuid);

        databaseManager.executeTransaction(connection -> {

            // Insert alliance
            String allianceSql = """
                INSERT INTO alliances (id, name, capital_guild_id, king_uuid, tax_rate, is_open, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

            try (PreparedStatement statement = connection.prepareStatement(allianceSql)) {
                statement.setString(1, alliance.getId());
                statement.setString(2, alliance.getName());
                statement.setString(3, alliance.getCapitalGuildId());
                statement.setString(4, kingUuid.toString());
                statement.setDouble(5, alliance.getTaxRate());
                statement.setBoolean(6, alliance.isOpen());
                statement.setString(7, alliance.getCreatedAt().format(DATE_FORMATTER));
                statement.executeUpdate();
            }

            // Add capital guild as member
            String memberSql = "INSERT INTO alliance_members (alliance_id, guild_id) VALUES (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(memberSql)) {
                statement.setString(1, alliance.getId());
                statement.setString(2, alliance.getCapitalGuildId());
                statement.executeUpdate();
            }
        });

        // Update cache
        alliancesById.put(alliance.getId(), alliance);
        alliancesByName.put(alliance.getName().toLowerCase(), alliance);

        logger.info("Created alliance: " + name);
    }

    @Override
    public void deleteAlliance(String name) {
        Optional<Alliance> allianceOpt = getAlliance(name);
        if (allianceOpt.isEmpty()) {
            return;
        }

        Alliance alliance = allianceOpt.get();

        databaseManager.executeTransaction(connection -> {
            // Delete relations first
            String deleteRelationsSql = "DELETE FROM alliance_relations WHERE alliance_id = ? OR other_alliance = ?";
            try (PreparedStatement statement = connection.prepareStatement(deleteRelationsSql)) {
                statement.setString(1, alliance.getId());
                statement.setString(2, alliance.getName());
                statement.executeUpdate();
            }

            // Delete ministers
            String deleteMinistersSql = "DELETE FROM alliance_ministers WHERE alliance_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(deleteMinistersSql)) {
                statement.setString(1, alliance.getId());
                statement.executeUpdate();
            }

            // Delete members
            String deleteMembersSql = "DELETE FROM alliance_members WHERE alliance_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(deleteMembersSql)) {
                statement.setString(1, alliance.getId());
                statement.executeUpdate();
            }

            // Delete alliance
            String deleteAllianceSql = "DELETE FROM alliances WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(deleteAllianceSql)) {
                statement.setString(1, alliance.getId());
                statement.executeUpdate();
            }
        });

        // Update cache
        alliancesById.remove(alliance.getId());
        alliancesByName.remove(alliance.getName().toLowerCase());

        logger.info("Deleted alliance: " + name);
    }

    @Override
    public GovernmentForm getGovernanceForm(String allianceId) {
        String sql = "SELECT governance_form FROM alliances WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, allianceId);
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

    @Override
    public void addGuild(Alliance alliance, String guildId) {
        databaseManager.executeTransaction(connection -> {
            String sql = "INSERT INTO alliance_members (alliance_id, guild_id) VALUES (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, alliance.getId());
                statement.setString(2, guildId);
                statement.executeUpdate();
            }
        });

        alliance.addGuild(guildId);
    }

    @Override
    public void removeGuild(Alliance alliance, String guildId) {
        if (alliance.getCapitalGuildId().equals(guildId)) {
            throw new IllegalArgumentException("Cannot remove capital town from alliance");
        }

        databaseManager.executeTransaction(connection -> {
            String sql = "DELETE FROM alliance_members WHERE alliance_id = ? AND guild_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, alliance.getId());
                statement.setString(2, guildId);
                statement.executeUpdate();
            }
        });

        alliance.removeGuild(guildId);
    }

    @Override
    public void setKing(Alliance alliance, UUID newKing) {
        databaseManager.executeTransaction(connection -> {
            String sql = "UPDATE alliances SET king_uuid = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, newKing.toString());
                statement.setString(2, alliance.getId());
                statement.executeUpdate();
            }
        });

        alliance.setKingUuid(newKing);
    }

    @Override
    public void addMinister(Alliance alliance, UUID minister) {
        databaseManager.executeTransaction(connection -> {
            String sql = "INSERT INTO alliance_ministers (alliance_id, player_uuid) VALUES (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, alliance.getId());
                statement.setString(2, minister.toString());
                statement.executeUpdate();
            }
        });

        alliance.addMinister(minister);
    }

    @Override
    public void removeMinister(Alliance alliance, UUID minister) {
        databaseManager.executeTransaction(connection -> {
            String sql = "DELETE FROM alliance_ministers WHERE alliance_id = ? AND player_uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, alliance.getId());
                statement.setString(2, minister.toString());
                statement.executeUpdate();
            }
        });

        alliance.removeMinister(minister);
    }

    private void updateAllianceRelation(Alliance alliance, String otherAlliance, String relationType, boolean isAdding) {
        databaseManager.executeTransaction(connection -> {
            if (isAdding) {
                String sql = "INSERT INTO alliance_relations (alliance_id, other_alliance, relation_type) VALUES (?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, alliance.getId());
                    statement.setString(2, otherAlliance);
                    statement.setString(3, relationType);
                    statement.executeUpdate();
                }
            } else {
                String sql = "DELETE FROM alliance_relations WHERE alliance_id = ? AND other_alliance = ? AND relation_type = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, alliance.getId());
                    statement.setString(2, otherAlliance);
                    statement.setString(3, relationType);
                    statement.executeUpdate();
                }
            }
        });
    }

    @Override
    public void addAlly(Alliance alliance, String otherAlliance) {
        // Remove as enemy if exists
        alliance.removeEnemy(otherAlliance);
        updateAllianceRelation(alliance, otherAlliance, "ENEMY", false);

        // Add as ally
        alliance.addAlly(otherAlliance);
        updateAllianceRelation(alliance, otherAlliance, "ALLY", true);
    }

    @Override
    public void removeAlly(Alliance alliance, String otherAlliance) {
        alliance.removeAlly(otherAlliance);
        updateAllianceRelation(alliance, otherAlliance, "ALLY", false);
    }

    @Override
    public void addEnemy(Alliance alliance, String otherAlliance) {
        // Remove as ally if exists
        alliance.removeAlly(otherAlliance);
        updateAllianceRelation(alliance, otherAlliance, "ALLY", false);

        // Add as enemy
        alliance.addEnemy(otherAlliance);
        updateAllianceRelation(alliance, otherAlliance, "ENEMY", true);
    }

    @Override
    public void removeEnemy(Alliance alliance, String otherAlliance) {
        alliance.removeEnemy(otherAlliance);
        updateAllianceRelation(alliance, otherAlliance, "ENEMY", false);
    }

    @Override
    public void setTaxRate(Alliance alliance, double rate) {
        databaseManager.executeTransaction(connection -> {
            String sql = "UPDATE alliances SET tax_rate = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setDouble(1, rate);
                statement.setString(2, alliance.getId());
                statement.executeUpdate();
            }
        });

        alliance.setTaxRate(rate);
    }

    @Override
    public void setOpen(Alliance alliance, boolean open) {
        databaseManager.executeTransaction(connection -> {
            String sql = "UPDATE alliances SET is_open = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setBoolean(1, open);
                statement.setString(2, alliance.getId());
                statement.executeUpdate();
            }
        });

        alliance.setOpen(open);
    }

    @Override
    public void updateAlliance(Alliance alliance) {
        databaseManager.executeTransaction(connection -> {
            String sql = """
                UPDATE alliances
                SET name = ?, capital_guild_id = ?, king_uuid = ?, tax_rate = ?, is_open = ?
                WHERE id = ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, alliance.getName());
                statement.setString(2, alliance.getCapitalGuildId());
                statement.setString(3, alliance.getKingUuid().toString());
                statement.setDouble(4, alliance.getTaxRate());
                statement.setBoolean(5, alliance.isOpen());
                statement.setString(6, alliance.getId());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void saveAlliance(Alliance alliance) {
        // Update cache
        alliancesById.put(alliance.getId(), alliance);
        alliancesByName.put(alliance.getName().toLowerCase(), alliance);

        // Update database
        updateAlliance(alliance);
    }
}