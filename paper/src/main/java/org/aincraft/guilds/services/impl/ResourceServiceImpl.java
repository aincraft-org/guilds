package org.aincraft.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.territory.persist.SqlSupport;
import org.aincraft.guilds.models.ResourceContribution;
import org.aincraft.guilds.models.ResourceType;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildResource;
import org.aincraft.guilds.services.ResourceService;
import org.aincraft.guilds.services.GuildService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Implementation of ResourceService for guild resource management and contribution tracking
 */

public class ResourceServiceImpl implements ResourceService {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;


    public ResourceServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager, GuildService guildService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public Optional<GuildResource> getGuildResource(String guildId, String resourceType) {
        Optional<ResourceType> parsed = parseResourceType(resourceType);
        if (guildId == null || parsed.isEmpty()) {
            return Optional.empty();
        }

        try {
            String sql = "SELECT * FROM guild_resources WHERE guild_id = ? AND resource_type = ?";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, guildId);
                statement.setString(2, parsed.get().getNormalizedName());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        GuildResource resource = mapResultSetToGuildResource(resultSet);
                        return Optional.of(resource);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get guild resource: " + e.getMessage(), e);
        }

        return Optional.empty();
    }

    @Override
    public List<GuildResource> getGuildResources(String guildId) {
        List<GuildResource> resources = new ArrayList<>();

        if (guildId == null) {
            return resources;
        }

        try {
            String sql = "SELECT * FROM guild_resources WHERE guild_id = ? ORDER BY resource_type";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, guildId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        GuildResource resource = mapResultSetToGuildResource(resultSet);
                        resources.add(resource);
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get guild resources: " + e.getMessage(), e);
        }

        return resources;
    }

    @Override
    public Map<String, GuildResource> getGuildResourceMap(Guild guild) {
        Map<String, GuildResource> resourceMap = new HashMap<>();

        if (guild == null) {
            return resourceMap;
        }

        List<GuildResource> resources = getGuildResources(guild.getId());
        for (GuildResource resource : resources) {
            resourceMap.put(resource.getResourceType().getNormalizedName(), resource);
        }

        return resourceMap;
    }

    @Override
    public boolean addGuildResources(String guildId, String resourceType, int amount) {
        Optional<ResourceType> parsed = parseResourceType(resourceType);
        if (guildId == null || guildId.isBlank() || parsed.isEmpty() || amount <= 0) {
            return false;
        }
        try {
            return databaseManager.executeTransactionWithResult(connection -> {
                creditGuildResources(connection, guildId, parsed.get(), amount);
                return true;
            }).orElse(false);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add guild resources", e);
            return false;
        }
    }

    @Override
    public boolean removeGuildResources(String guildId, String resourceType, int amount) {
        Optional<ResourceType> parsed = parseResourceType(resourceType);
        if (guildId == null || guildId.isBlank() || parsed.isEmpty() || amount <= 0) {
            return false;
        }
        try {
            return databaseManager.executeTransactionWithResult(connection ->
                    debitGuildResources(connection, guildId, parsed.get(), amount)).orElse(false);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove guild resources", e);
            return false;
        }
    }

    @Override
    public boolean hasSufficientResources(String guildId, String resourceType, int requiredAmount) {
        Optional<GuildResource> resourceOpt = getGuildResource(guildId, resourceType);
        return resourceOpt.map(resource -> resource.hasSufficientResources(requiredAmount)).orElse(false);
    }

    @Override
    public Optional<ResourceContribution> getResourceContribution(String contributionId) {
        if (contributionId == null || contributionId.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM resource_contributions WHERE id = ?")) {
            statement.setString(1, contributionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.ofNullable(mapResultSetToContribution(result))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get resource contribution", e);
            return Optional.empty();
        }
    }

    @Override
    public List<ResourceContribution> getGuildContributions(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            return List.of();
        }
        return queryContributions(
                "SELECT * FROM resource_contributions WHERE guild_id = ? "
                        + "ORDER BY contribution_time DESC, id DESC",
                guildId);
    }

    @Override
    public List<ResourceContribution> getPlayerContributions(UUID contributorUuid) {
        if (contributorUuid == null) {
            return List.of();
        }
        return queryContributions(
                "SELECT * FROM resource_contributions WHERE contributor_uuid = ? "
                        + "ORDER BY contribution_time DESC, id DESC",
                contributorUuid);
    }

    @Override
    public List<ResourceContribution> getPlayerContributionsToGuild(
            String guildId, UUID contributorUuid) {
        if (guildId == null || guildId.isBlank() || contributorUuid == null) {
            return List.of();
        }
        return queryContributions(
                "SELECT * FROM resource_contributions WHERE guild_id = ? AND contributor_uuid = ? "
                        + "ORDER BY contribution_time DESC, id DESC",
                guildId, contributorUuid);
    }

    @Override
    public Optional<ResourceContribution> recordResourceContribution(
            String guildId, UUID contributorUuid, String resourceType, int amount) {
        Optional<ResourceType> parsed = parseResourceType(resourceType);
        if (guildId == null || guildId.isBlank() || contributorUuid == null
                || parsed.isEmpty() || amount <= 0) {
            return Optional.empty();
        }

        ResourceContribution contribution = new ResourceContribution(
                guildId, contributorUuid, parsed.get(), amount);
        try {
            return databaseManager.executeTransactionWithResult(connection -> {
                insertContribution(connection, contribution);
                return contribution;
            });
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to record resource contribution", e);
            return Optional.empty();
        }
    }

    @Override
    public Map<String, Integer> calculateTotalContributionsByResource(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            return Map.of();
        }
        Map<String, Integer> totals = new LinkedHashMap<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT resource_type, SUM(amount) AS total FROM resource_contributions "
                             + "WHERE guild_id = ? GROUP BY resource_type ORDER BY resource_type")) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ResourceType type = parseResourceType(result.getString("resource_type")).orElse(null);
                    if (type != null) {
                        totals.put(type.getNormalizedName(), result.getInt("total"));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to calculate guild contribution totals", e);
        }
        return Map.copyOf(totals);
    }

    @Override
    public Map<String, Integer> calculatePlayerContributions(UUID contributorUuid) {
        if (contributorUuid == null) {
            return Map.of();
        }
        Map<String, Integer> totals = new LinkedHashMap<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT resource_type, SUM(amount) AS total FROM resource_contributions "
                             + "WHERE contributor_uuid = ? GROUP BY resource_type ORDER BY resource_type")) {
            statement.setString(1, contributorUuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ResourceType type = parseResourceType(result.getString("resource_type")).orElse(null);
                    if (type != null) {
                        totals.put(type.getNormalizedName(), result.getInt("total"));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to calculate player contribution totals", e);
        }
        return Map.copyOf(totals);
    }

    @Override
    public List<ResourceContribution> getRecentContributions(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            return List.of();
        }
        return queryContributions(
                "SELECT * FROM resource_contributions WHERE guild_id = ? AND contribution_time >= ? "
                        + "ORDER BY contribution_time DESC, id DESC",
                guildId, LocalDateTime.now().minusDays(1));
    }

    @Override
    public ContributionStatistics getContributionStatistics(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            return new ContributionStatistics(0, 0, Map.of(), Map.of(), null);
        }
        int contributors = 0;
        int contributionCount = 0;
        LocalDateTime lastContribution = null;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(DISTINCT contributor_uuid), COUNT(*), MAX(contribution_time) "
                             + "FROM resource_contributions WHERE guild_id = ?")) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    contributors = result.getInt(1);
                    contributionCount = result.getInt(2);
                    String last = result.getString(3);
                    if (last != null) {
                        lastContribution = LocalDateTime.parse(last);
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to calculate contribution statistics", e);
        }

        Map<String, Integer> topContributors = new LinkedHashMap<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT contributor_uuid, SUM(amount) AS total FROM resource_contributions "
                             + "WHERE guild_id = ? GROUP BY contributor_uuid ORDER BY total DESC, contributor_uuid")) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    topContributors.put(result.getString("contributor_uuid"), result.getInt("total"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to calculate top contributors", e);
        }
        return new ContributionStatistics(
                contributors,
                contributionCount,
                calculateTotalContributionsByResource(guildId),
                Map.copyOf(topContributors),
                lastContribution);
    }

    @Override
    public ContributionValidation validateContribution(
            Guild guild, UUID contributorUuid, String resourceType, int amount) {
        Optional<ResourceType> parsed = parseResourceType(resourceType);
        if (guild == null || contributorUuid == null || parsed.isEmpty() || amount <= 0) {
            return new ContributionValidation(false, "Invalid contribution parameters", false, false);
        }
        if (!guild.isResident(contributorUuid)) {
            return new ContributionValidation(false, "You are not a resident of this guild", false, false);
        }

        boolean canAfford = checkPlayerResources(
                contributorUuid, parsed.get().getNormalizedName(), amount);
        return new ContributionValidation(
                canAfford,
                canAfford ? "Valid contribution" : "Insufficient resources in inventory",
                canAfford,
                canAfford);
    }

    @Override
    public ContributionResult processContribution(
            Guild guild, UUID contributorUuid, String resourceType, int amount) {
        ContributionValidation validation = validateContribution(guild, contributorUuid, resourceType, amount);
        if (!validation.isValid()) {
            return new ContributionResult(false, validation.getReason(), null, 0);
        }
        if (!validation.canAfford()) {
            return new ContributionResult(false, "You don't have enough resources to contribute", null, 0);
        }

        ResourceType parsed = parseResourceType(resourceType).orElseThrow();
        String normalized = parsed.getNormalizedName();
        boolean removed = false;
        try {
            if (!removePlayerResources(contributorUuid, normalized, amount)) {
                return new ContributionResult(false, "Failed to remove resources from inventory", null, 0);
            }
            removed = true;

            ResourceContribution contribution = new ResourceContribution(
                    guild.getId(), contributorUuid, parsed, amount);
            Optional<ResourceContribution> committed = databaseManager.executeTransactionWithResult(connection -> {
                creditGuildResources(connection, guild.getId(), parsed, amount);
                insertContribution(connection, contribution);
                updateUpgradeProgress(connection, guild.getId(), normalized, amount);
                return contribution;
            });
            if (committed == null || committed.isEmpty()) {
                refundAfterFailure(contributorUuid, normalized, amount);
                return new ContributionResult(false, "Failed to persist contribution", null, 0);
            }

            // The object becomes dirty only after the SQL transaction commits.
            guild.contributeToUpgrade(normalized, amount);
            return new ContributionResult(
                    true,
                    "Successfully contributed " + amount + " " + normalized,
                    committed.get(),
                    amount);
        } catch (Exception e) {
            if (removed) {
                refundAfterFailure(contributorUuid, normalized, amount);
            }
            plugin.getLogger().log(Level.SEVERE, "Error processing contribution", e);
            return new ContributionResult(false, "Internal error during contribution", null, 0);
        }
    }

    @Override
    public List<String> getSupportedResourceTypes() {
        return Arrays.stream(ResourceType.values())
                .map(ResourceType::getNormalizedName)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isSupportedResourceType(String resourceType) {
        return parseResourceType(resourceType).isPresent();
    }

    @Override
    public boolean clearGuildResourceData(String guildId) {
        if (guildId == null) {
            return false;
        }

        try {
            String sql = "DELETE FROM guild_resources WHERE guild_id = ?";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, guildId);
                int deletedRows = statement.executeUpdate();
                return deletedRows > 0;
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to clear guild resource data: " + e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void resetAllResourceData() {
        try {
            String sql = "DELETE FROM guild_resources";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                int deletedRows = statement.executeUpdate();
                plugin.getLogger().info("Deleted " + deletedRows + " guild resource records");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reset resource data: " + e.getMessage(), e);
        }
    }

    private Optional<ResourceType> parseResourceType(String resourceType) {
        return ResourceType.fromString(resourceType);
    }

    private List<ResourceContribution> queryContributions(String sql, Object... parameters) {
        List<ResourceContribution> contributions = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                bindParameter(statement, i + 1, parameters[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ResourceContribution contribution = mapResultSetToContribution(result);
                    if (contribution != null) {
                        contributions.add(contribution);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to query resource contributions", e);
        }
        return List.copyOf(contributions);
    }

    private static void bindParameter(PreparedStatement statement, int index, Object value)
            throws SQLException {
        if (value instanceof UUID uuid) {
            statement.setString(index, uuid.toString());
        } else if (value instanceof LocalDateTime time) {
            statement.setString(index, time.toString());
        } else {
            statement.setString(index, String.valueOf(value));
        }
    }

    private ResourceContribution mapResultSetToContribution(ResultSet result) throws SQLException {
        ResourceType type = parseResourceType(result.getString("resource_type")).orElse(null);
        if (type == null) {
            return null;
        }
        try {
            return new ResourceContribution(
                    result.getString("id"),
                    result.getString("guild_id"),
                    UUID.fromString(result.getString("contributor_uuid")),
                    type,
                    result.getInt("amount"),
                    LocalDateTime.parse(result.getString("contribution_time")));
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid contribution row", e);
        }
    }

    private void insertContribution(Connection connection, ResourceContribution contribution)
            throws SQLException {
        String sql = """
                INSERT INTO resource_contributions
                    (id, guild_id, contributor_uuid, resource_type, amount, contribution_time)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, contribution.getId());
            statement.setString(2, contribution.getGuildId());
            statement.setString(3, contribution.getContributorUuid().toString());
            statement.setString(4, contribution.getResourceType().getNormalizedName());
            statement.setInt(5, contribution.getAmount());
            statement.setString(6, contribution.getContributionTime().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Contribution insert did not affect one row");
            }
        }
    }

    private void creditGuildResources(
            Connection connection, String guildId, ResourceType resourceType, int amount)
            throws SQLException {
        String sql = SqlSupport.upsertSql(connection, """
                INSERT INTO guild_resources (id, guild_id, resource_type, amount, last_updated)
                VALUES (?, ?, ?, ?, ?)
                """, "guild_id, resource_type", """
                    amount = guild_resources.amount + EXCLUDED.amount,
                    last_updated = EXCLUDED.last_updated
                """);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, guildId);
            statement.setString(3, resourceType.getNormalizedName());
            statement.setInt(4, amount);
            statement.setString(5, LocalDateTime.now().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Guild resource credit did not affect one row");
            }
        }
    }

    private boolean debitGuildResources(
            Connection connection, String guildId, ResourceType resourceType, int amount)
            throws SQLException {
        int available;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT amount FROM guild_resources WHERE guild_id = ? AND resource_type = ? FOR UPDATE")) {
            statement.setString(1, guildId);
            statement.setString(2, resourceType.getNormalizedName());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return false;
                }
                available = result.getInt(1);
            }
        }
        if (available < amount) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE guild_resources SET amount = amount - ?, last_updated = ? "
                        + "WHERE guild_id = ? AND resource_type = ?")) {
            statement.setInt(1, amount);
            statement.setString(2, LocalDateTime.now().toString());
            statement.setString(3, guildId);
            statement.setString(4, resourceType.getNormalizedName());
            return statement.executeUpdate() == 1;
        }
    }

    private void updateUpgradeProgress(
            Connection connection, String guildId, String resourceType, int amount)
            throws SQLException {
        String currentJson;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT upgrade_progress FROM guilds WHERE id = ? FOR UPDATE")) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Guild not found: " + guildId);
                }
                currentJson = result.getString(1);
            }
        }
        Map<String, Integer> progress = parseProgressJson(currentJson);
        progress.merge(resourceType, amount, Math::addExact);
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE guilds SET upgrade_progress = ? WHERE id = ?")) {
            statement.setString(1, serializeProgressJson(progress));
            statement.setString(2, guildId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Guild progress update did not affect one row");
            }
        }
    }

    private Map<String, Integer> parseProgressJson(String json) {
        Map<String, Integer> progress = new LinkedHashMap<>();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return progress;
        }
        String content = json.trim();
        if (content.startsWith("{") && content.endsWith("}")) {
            content = content.substring(1, content.length() - 1);
        }
        if (content.isBlank()) {
            return progress;
        }
        for (String pair : content.split(",")) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length != 2) {
                continue;
            }
            String key = keyValue[0].trim().replace("\"", "");
            try {
                int value = Integer.parseInt(keyValue[1].trim());
                if (!key.isBlank() && value > 0) {
                    progress.put(key, value);
                }
            } catch (NumberFormatException ignored) {
                // Invalid legacy progress is ignored rather than credited.
            }
        }
        return progress;
    }

    private String serializeProgressJson(Map<String, Integer> progress) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : progress.entrySet()) {
            if (!first) {
                json.append(',');
            }
            json.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        return json.append('}').toString();
    }

    private void refundAfterFailure(UUID contributorUuid, String resourceType, int amount) {
        if (!addPlayerResources(contributorUuid, resourceType, amount)) {
            plugin.getLogger().warning(
                    "Could not refund " + amount + " " + resourceType
                            + " to contributor " + contributorUuid);
        }
    }

    /**
     * Map a ResultSet to a GuildResource object
     */
    private GuildResource mapResultSetToGuildResource(ResultSet resultSet) throws SQLException {
        String resourceTypeStr = resultSet.getString("resource_type");
        ResourceType resourceType = ResourceType.fromString(resourceTypeStr).orElse(ResourceType.DIAMOND);

        return new GuildResource(
                resultSet.getString("id"),
                resultSet.getString("guild_id"),
                resourceType,
                resultSet.getInt("amount"),
                LocalDateTime.parse(resultSet.getString("last_updated"))
        );
    }

    /**
     * Check if player has sufficient resources in inventory
     */
    private boolean checkPlayerResources(UUID playerUuid, String resourceType, int amount) {
        try {
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                return false;
            }

            Material material = getMaterialForResource(resourceType);
            if (material == null) {
                return false;
            }

            int playerAmount = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == material) {
                    playerAmount += item.getAmount();
                }
            }

            return playerAmount >= amount;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking player resources: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Remove resources from player inventory
     */
    private boolean removePlayerResources(UUID playerUuid, String resourceType, int amount) {
        try {
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                return false;
            }

            Material material = getMaterialForResource(resourceType);
            if (material == null) {
                return false;
            }

            int remaining = amount;
            ItemStack[] contents = player.getInventory().getContents();

            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack item = contents[i];
                if (item != null && item.getType() == material) {
                    int stackAmount = item.getAmount();
                    if (stackAmount <= remaining) {
                        remaining -= stackAmount;
                        contents[i] = null;
                    } else {
                        item.setAmount(stackAmount - remaining);
                        remaining = 0;
                    }
                }
            }

            player.getInventory().setContents(contents);
            player.updateInventory();

            return remaining == 0;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error removing player resources: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Add resources to player inventory (for refunds)
     */
    private boolean addPlayerResources(UUID playerUuid, String resourceType, int amount) {
        try {
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                return false;
            }

            Material material = getMaterialForResource(resourceType);
            if (material == null) {
                return false;
            }

            ItemStack item = createResourceStack(material, amount);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);

            return leftover.isEmpty(); // Return true if all items were added

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error adding player resources: " + e.getMessage(), e);
            return false;
        }
    }

    /** Creates a refund stack; isolated for server/inventory test seams. */
    protected ItemStack createResourceStack(Material material, int amount) {
        return new ItemStack(material, amount);
    }
    /**
     * Get Minecraft material for resource type
     */
    private Material getMaterialForResource(String resourceType) {
        switch (resourceType.toLowerCase(Locale.ROOT)) {
            case "diamond": return Material.DIAMOND;
            case "gold": return Material.GOLD_INGOT;
            case "iron": return Material.IRON_INGOT;
            case "emerald": return Material.EMERALD;
            case "experience": return Material.EXPERIENCE_BOTTLE;
            default: return null;
        }
    }
}