package org.aincraft.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Nation;
import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.services.NationService;
import org.aincraft.guilds.services.TownService;

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
 * Implementation of NationService with database operations
 */

public class NationServiceImpl implements NationService {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final DataSource dataSource;
    private final Logger logger;
    private final TownService townService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Cache nations in memory for quick access
    private final Map<String, Nation> nationsById = new HashMap<>();
    private final Map<String, Nation> nationsByName = new HashMap<>();


    public NationServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager, Logger logger, TownService townService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
        this.townService = townService;

        // Load all nations from database on startup
        loadAllNations();
    }

    private void loadAllNations() {
        String sql = """
            SELECT n.id, n.name, n.capital_town_id, n.king_uuid, n.tax_rate, n.is_open, n.created_at,
                   GROUP_CONCAT(DISTINCT nm.town_id) as member_towns,
                   GROUP_CONCAT(DISTINCT nmin.player_uuid) as ministers,
                   GROUP_CONCAT(DISTINCT nr.other_nation) as relations,
                   GROUP_CONCAT(DISTINCT nr.relation_type) as relation_types
            FROM nations n
            LEFT JOIN nation_members nm ON n.id = nm.nation_id
            LEFT JOIN nation_ministers nmin ON n.id = nmin.nation_id
            LEFT JOIN nation_relations nr ON n.id = nr.nation_id
            GROUP BY n.id, n.name, n.capital_town_id, n.king_uuid, n.tax_rate, n.is_open, n.created_at
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                Nation nation = loadNationFromResult(resultSet);
                nationsById.put(nation.getId(), nation);
                nationsByName.put(nation.getName().toLowerCase(), nation);
            }

            logger.info("Loaded " + nationsById.size() + " nations from database");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load nations from database", e);
        }
    }

    private Nation loadNationFromResult(ResultSet resultSet) throws SQLException {
        Nation nation = new Nation();

        nation.setId(resultSet.getString("id"));
        nation.setName(resultSet.getString("name"));
        nation.setCapitalTownId(resultSet.getString("capital_town_id"));
        nation.setKingUuid(UUID.fromString(resultSet.getString("king_uuid")));
        nation.setTaxRate(resultSet.getDouble("tax_rate"));
        nation.setOpen(resultSet.getBoolean("is_open"));

        String createdAtStr = resultSet.getString("created_at");
        if (createdAtStr != null) {
            nation.setCreatedAt(LocalDateTime.parse(createdAtStr, DATE_FORMATTER));
        }

        // Load member towns
        String memberTownsStr = resultSet.getString("member_towns");
        if (memberTownsStr != null) {
            nation.setMemberTownIds(new HashSet<>(Arrays.asList(memberTownsStr.split(","))));
        }

        // Load ministers
        String ministersStr = resultSet.getString("ministers");
        if (ministersStr != null) {
            Set<UUID> ministers = new HashSet<>();
            for (String minister : ministersStr.split(",")) {
                ministers.add(UUID.fromString(minister));
            }
            nation.setMinisters(ministers);
        }

        // Load alliances and enemies
        String relationsStr = resultSet.getString("relations");
        String relationTypesStr = resultSet.getString("relation_types");

        if (relationsStr != null && relationTypesStr != null) {
            String[] relations = relationsStr.split(",");
            String[] relationTypes = relationTypesStr.split(",");

            for (int i = 0; i < Math.min(relations.length, relationTypes.length); i++) {
                String nationName = relations[i];
                String relationType = relationTypes[i];

                if ("ALLY".equals(relationType)) {
                    nation.addAlly(nationName);
                } else if ("ENEMY".equals(relationType)) {
                    nation.addEnemy(nationName);
                }
            }
        }

        return nation;
    }

    @Override
    public Optional<Nation> getNation(String name) {
        return Optional.ofNullable(nationsByName.get(name.toLowerCase()));
    }

    @Override
    public Optional<Nation> getNationById(String id) {
        return Optional.ofNullable(nationsById.get(id));
    }

    @Override
    public List<Nation> getAllNations() {
        return new ArrayList<>(nationsById.values());
    }

    @Override
    public void createNation(String name, Town capitalTown, UUID kingUuid) {
        if (getNation(name).isPresent()) {
            throw new IllegalArgumentException("Nation already exists: " + name);
        }
        Nation nation = new Nation(name, capitalTown.getId(), kingUuid);

        databaseManager.executeTransaction(connection -> {

            // Insert nation
            String nationSql = """
                INSERT INTO nations (id, name, capital_town_id, king_uuid, tax_rate, is_open, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

            try (PreparedStatement statement = connection.prepareStatement(nationSql)) {
                statement.setString(1, nation.getId());
                statement.setString(2, nation.getName());
                statement.setString(3, nation.getCapitalTownId());
                statement.setString(4, kingUuid.toString());
                statement.setDouble(5, nation.getTaxRate());
                statement.setBoolean(6, nation.isOpen());
                statement.setString(7, nation.getCreatedAt().format(DATE_FORMATTER));
                statement.executeUpdate();
            }

            // Add capital town as member
            String memberSql = "INSERT INTO nation_members (nation_id, town_id) VALUES (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(memberSql)) {
                statement.setString(1, nation.getId());
                statement.setString(2, nation.getCapitalTownId());
                statement.executeUpdate();
            }
        });

        // Update cache
        nationsById.put(nation.getId(), nation);
        nationsByName.put(nation.getName().toLowerCase(), nation);

        logger.info("Created nation: " + name);
    }

    @Override
    public void deleteNation(String name) {
        Optional<Nation> nationOpt = getNation(name);
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();

        databaseManager.executeTransaction(connection -> {
            // Delete relations first
            String deleteRelationsSql = "DELETE FROM nation_relations WHERE nation_id = ? OR other_nation = ?";
            try (PreparedStatement statement = connection.prepareStatement(deleteRelationsSql)) {
                statement.setString(1, nation.getId());
                statement.setString(2, nation.getName());
                statement.executeUpdate();
            }

            // Delete ministers
            String deleteMinistersSql = "DELETE FROM nation_ministers WHERE nation_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(deleteMinistersSql)) {
                statement.setString(1, nation.getId());
                statement.executeUpdate();
            }

            // Delete members
            String deleteMembersSql = "DELETE FROM nation_members WHERE nation_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(deleteMembersSql)) {
                statement.setString(1, nation.getId());
                statement.executeUpdate();
            }

            // Delete nation
            String deleteNationSql = "DELETE FROM nations WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(deleteNationSql)) {
                statement.setString(1, nation.getId());
                statement.executeUpdate();
            }
        });

        // Update cache
        nationsById.remove(nation.getId());
        nationsByName.remove(nation.getName().toLowerCase());

        logger.info("Deleted nation: " + name);
    }

    @Override
    public void addTown(Nation nation, String townId) {
        databaseManager.executeTransaction(connection -> {
            String sql = "INSERT INTO nation_members (nation_id, town_id) VALUES (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nation.getId());
                statement.setString(2, townId);
                statement.executeUpdate();
            }
        });

        nation.addTown(townId);
    }

    @Override
    public void removeTown(Nation nation, String townId) {
        if (nation.getCapitalTownId().equals(townId)) {
            throw new IllegalArgumentException("Cannot remove capital town from nation");
        }

        databaseManager.executeTransaction(connection -> {
            String sql = "DELETE FROM nation_members WHERE nation_id = ? AND town_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nation.getId());
                statement.setString(2, townId);
                statement.executeUpdate();
            }
        });

        nation.removeTown(townId);
    }

    @Override
    public void setKing(Nation nation, UUID newKing) {
        databaseManager.executeTransaction(connection -> {
            String sql = "UPDATE nations SET king_uuid = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, newKing.toString());
                statement.setString(2, nation.getId());
                statement.executeUpdate();
            }
        });

        nation.setKingUuid(newKing);
    }

    @Override
    public void addMinister(Nation nation, UUID minister) {
        databaseManager.executeTransaction(connection -> {
            String sql = "INSERT INTO nation_ministers (nation_id, player_uuid) VALUES (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nation.getId());
                statement.setString(2, minister.toString());
                statement.executeUpdate();
            }
        });

        nation.addMinister(minister);
    }

    @Override
    public void removeMinister(Nation nation, UUID minister) {
        databaseManager.executeTransaction(connection -> {
            String sql = "DELETE FROM nation_ministers WHERE nation_id = ? AND player_uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nation.getId());
                statement.setString(2, minister.toString());
                statement.executeUpdate();
            }
        });

        nation.removeMinister(minister);
    }

    private void updateNationRelation(Nation nation, String otherNation, String relationType, boolean isAdding) {
        databaseManager.executeTransaction(connection -> {
            if (isAdding) {
                String sql = "INSERT INTO nation_relations (nation_id, other_nation, relation_type) VALUES (?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, nation.getId());
                    statement.setString(2, otherNation);
                    statement.setString(3, relationType);
                    statement.executeUpdate();
                }
            } else {
                String sql = "DELETE FROM nation_relations WHERE nation_id = ? AND other_nation = ? AND relation_type = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, nation.getId());
                    statement.setString(2, otherNation);
                    statement.setString(3, relationType);
                    statement.executeUpdate();
                }
            }
        });
    }

    @Override
    public void addAlly(Nation nation, String otherNation) {
        // Remove as enemy if exists
        nation.removeEnemy(otherNation);
        updateNationRelation(nation, otherNation, "ENEMY", false);

        // Add as ally
        nation.addAlly(otherNation);
        updateNationRelation(nation, otherNation, "ALLY", true);
    }

    @Override
    public void removeAlly(Nation nation, String otherNation) {
        nation.removeAlly(otherNation);
        updateNationRelation(nation, otherNation, "ALLY", false);
    }

    @Override
    public void addEnemy(Nation nation, String otherNation) {
        // Remove as ally if exists
        nation.removeAlly(otherNation);
        updateNationRelation(nation, otherNation, "ALLY", false);

        // Add as enemy
        nation.addEnemy(otherNation);
        updateNationRelation(nation, otherNation, "ENEMY", true);
    }

    @Override
    public void removeEnemy(Nation nation, String otherNation) {
        nation.removeEnemy(otherNation);
        updateNationRelation(nation, otherNation, "ENEMY", false);
    }

    @Override
    public void setTaxRate(Nation nation, double rate) {
        databaseManager.executeTransaction(connection -> {
            String sql = "UPDATE nations SET tax_rate = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setDouble(1, rate);
                statement.setString(2, nation.getId());
                statement.executeUpdate();
            }
        });

        nation.setTaxRate(rate);
    }

    @Override
    public void setOpen(Nation nation, boolean open) {
        databaseManager.executeTransaction(connection -> {
            String sql = "UPDATE nations SET is_open = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setBoolean(1, open);
                statement.setString(2, nation.getId());
                statement.executeUpdate();
            }
        });

        nation.setOpen(open);
    }

    @Override
    public void updateNation(Nation nation) {
        databaseManager.executeTransaction(connection -> {
            String sql = """
                UPDATE nations
                SET name = ?, capital_town_id = ?, king_uuid = ?, tax_rate = ?, is_open = ?
                WHERE id = ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nation.getName());
                statement.setString(2, nation.getCapitalTownId());
                statement.setString(3, nation.getKingUuid().toString());
                statement.setDouble(4, nation.getTaxRate());
                statement.setBoolean(5, nation.isOpen());
                statement.setString(6, nation.getId());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public void saveNation(Nation nation) {
        // Update cache
        nationsById.put(nation.getId(), nation);
        nationsByName.put(nation.getName().toLowerCase(), nation);

        // Update database
        updateNation(nation);
    }
}