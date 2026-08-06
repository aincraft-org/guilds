package org.aincraft.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.database.DatabaseManager;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * Implementation of ResourceService for guild resource management and contribution tracking
 */

public class ResourceServiceImpl implements ResourceService {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final GuildService guildService;


    public ResourceServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager, GuildService guildService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.guildService = guildService;
    }

    @Override
    public Optional<GuildResource> getGuildResource(String guildId, String resourceType) {
        if (guildId == null || resourceType == null || !isSupportedResourceType(resourceType)) {
            return Optional.empty();
        }

        try {
            String sql = "SELECT * FROM guild_resources WHERE guild_id = ? AND resource_type = ?";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, guildId);
                statement.setString(2, resourceType.toLowerCase());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        GuildResource resource = mapResultSetToGuildResource(resultSet);
                        return Optional.of(resource);
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get town resource: " + e.getMessage(), e);
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
            plugin.getLogger().log(Level.SEVERE, "Failed to get town resources: " + e.getMessage(), e);
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
        if (guildId == null || resourceType == null || amount <= 0 || !isSupportedResourceType(resourceType)) {
            return false;
        }

        try {
            Optional<GuildResource> existingResourceOpt = getGuildResource(guildId, resourceType);

            if (existingResourceOpt.isPresent()) {
                // Update existing resource
                GuildResource resource = existingResourceOpt.get();
                resource.addResources(amount);

                String sql = "UPDATE guild_resources SET amount = ?, last_updated = ? WHERE guild_id = ? AND resource_type = ?";

                try (Connection connection = databaseManager.getConnection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {

                    statement.setInt(1, resource.getAmount());
                    statement.setString(2, LocalDateTime.now().toString());
                    statement.setString(3, guildId);
                    statement.setString(4, resourceType.toLowerCase());

                    int updatedRows = statement.executeUpdate();
                    return updatedRows > 0;
                }

            } else {
                // Create new resource entry
                String sql = "INSERT INTO guild_resources (id, guild_id, resource_type, amount, last_updated) VALUES (?, ?, ?, ?, ?)";

                try (Connection connection = databaseManager.getConnection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {

                    ResourceType resType = ResourceType.fromString(resourceType).orElse(null);
                    if (resType == null) {
                        return false;
                    }

                    GuildResource newResource = new GuildResource(guildId, resType);
                    newResource.addResources(amount);

                    statement.setString(1, newResource.getId());
                    statement.setString(2, guildId);
                    statement.setString(3, resType.getNormalizedName());
                    statement.setInt(4, newResource.getAmount());
                    statement.setString(5, newResource.getLastUpdated().toString());

                    int insertedRows = statement.executeUpdate();
                    return insertedRows > 0;
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add town resources: " + e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean removeGuildResources(String guildId, String resourceType, int amount) {
        if (guildId == null || resourceType == null || amount <= 0 || !isSupportedResourceType(resourceType)) {
            return false;
        }

        Optional<GuildResource> resourceOpt = getGuildResource(guildId, resourceType);
        if (resourceOpt.isEmpty()) {
            return false;
        }

        GuildResource resource = resourceOpt.get();
        if (!resource.hasSufficientResources(amount)) {
            return false;
        }

        try {
            resource.removeResources(amount);

            String sql = "UPDATE guild_resources SET amount = ?, last_updated = ? WHERE guild_id = ? AND resource_type = ?";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setInt(1, resource.getAmount());
                statement.setString(2, LocalDateTime.now().toString());
                statement.setString(3, guildId);
                statement.setString(4, resourceType.toLowerCase());

                int updatedRows = statement.executeUpdate();
                return updatedRows > 0;
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove town resources: " + e.getMessage(), e);
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
        // Implementation would fetch from database
        // For now, return empty
        return Optional.empty();
    }

    @Override
    public List<ResourceContribution> getGuildContributions(String guildId) {
        // Implementation would fetch from database
        // For now, return empty list
        return List.of();
    }

    @Override
    public List<ResourceContribution> getPlayerContributions(UUID contributorUuid) {
        // Implementation would fetch from database
        // For now, return empty list
        return List.of();
    }

    @Override
    public List<ResourceContribution> getPlayerContributionsToGuild(String guildId, UUID contributorUuid) {
        // Implementation would fetch from database
        // For now, return empty list
        return List.of();
    }

    @Override
    public Optional<ResourceContribution> recordResourceContribution(String guildId, UUID contributorUuid, String resourceType, int amount) {
        // Implementation would create and save to database
        // For now, create a contribution object without saving
        ResourceType resType = ResourceType.fromString(resourceType).orElse(null);
        if (resType == null) {
            return Optional.empty();
        }
        ResourceContribution contribution = new ResourceContribution(guildId, contributorUuid, resType, amount);
        return Optional.of(contribution);
    }

    @Override
    public Map<String, Integer> calculateTotalContributionsByResource(String guildId) {
        Map<String, Integer> totals = new HashMap<>();

        // Implementation would sum from database
        // For now, return empty map (will be populated by actual contributions)

        return totals;
    }

    @Override
    public Map<String, Integer> calculatePlayerContributions(UUID contributorUuid) {
        Map<String, Integer> totals = new HashMap<>();

        // Implementation would sum from database
        // For now, return empty map (will be populated by actual contributions)

        return totals;
    }

    @Override
    public List<ResourceContribution> getRecentContributions(String guildId) {
        // Implementation would fetch recent contributions from database
        // For now, return empty list
        return List.of();
    }

    @Override
    public ContributionStatistics getContributionStatistics(String guildId) {
        // Implementation would calculate real statistics
        return new ContributionStatistics(
                0, // total contributors
                0, // total contributions
                calculateTotalContributionsByResource(guildId), // resource totals
                Map.of(), // top contributors
                LocalDateTime.now() // last contribution
        );
    }

    @Override
    public ContributionValidation validateContribution(Guild guild, UUID contributorUuid, String resourceType, int amount) {
        if (guild == null || contributorUuid == null || resourceType == null || amount <= 0) {
            return new ContributionValidation(false, "Invalid contribution parameters", false, false);
        }

        if (!isSupportedResourceType(resourceType)) {
            return new ContributionValidation(false, "Unsupported resource type: " + resourceType, false, false);
        }

        // Check if player is resident of the guild
        if (!guild.isResident(contributorUuid)) {
            return new ContributionValidation(false, "You are not a resident of this town", false, false);
        }

        // Check if player has sufficient resources in inventory
        boolean canAfford = checkPlayerResources(contributorUuid, resourceType, amount);

        return new ContributionValidation(
                canAfford,
                canAfford ? "Valid contribution" : "Insufficient resources in inventory",
                canAfford,
                canAfford
        );
    }

    @Override
    public ContributionResult processContribution(Guild guild, UUID contributorUuid, String resourceType, int amount) {
        // Validate contribution
        ContributionValidation validation = validateContribution(guild, contributorUuid, resourceType, amount);

        if (!validation.isValid()) {
            return new ContributionResult(false, validation.getReason(), null, 0);
        }

        if (!validation.canAfford()) {
            return new ContributionResult(false, "You don't have enough resources to contribute", null, 0);
        }

        try {
            // Remove resources from player inventory
            if (!removePlayerResources(contributorUuid, resourceType, amount)) {
                return new ContributionResult(false, "Failed to remove resources from inventory", null, 0);
            }

            // Add to guild's resource bank
            if (!addGuildResources(guild.getId(), resourceType, amount)) {
                // Refund player if guild resource addition failed
                addPlayerResources(contributorUuid, resourceType, amount);
                return new ContributionResult(false, "Failed to add resources to town bank", null, 0);
            }

            // Record contribution
            Optional<ResourceContribution> contributionOpt = recordResourceContribution(guild.getId(), contributorUuid, resourceType, amount);

            if (contributionOpt.isPresent()) {
                // Update guild's upgrade progress
                guild.contributeToUpgrade(resourceType, amount);
                guildService.updateGuild(guild);

                return new ContributionResult(true, "Successfully contributed " + amount + " " + resourceType, contributionOpt.get(), amount);
            } else {
                return new ContributionResult(false, "Failed to record contribution", null, 0);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error processing contribution: " + e.getMessage(), e);
            return new ContributionResult(false, "Internal error during contribution", null, 0);
        }
    }

    @Override
    public List<String> getSupportedResourceTypes() {
        // Return all Bukkit Material types that are items
        return Arrays.stream(Material.values())
                .filter(Material::isItem)
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isSupportedResourceType(String resourceType) {
        if (resourceType == null) {
            return false;
        }

        try {
            Material material = Material.valueOf(resourceType.toUpperCase());
            return material.isItem();
        } catch (IllegalArgumentException e) {
            return false;
        }
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
            plugin.getLogger().log(Level.SEVERE, "Failed to clear town resource data: " + e.getMessage(), e);
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
                plugin.getLogger().info("Deleted " + deletedRows + " town resource records");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reset resource data: " + e.getMessage(), e);
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

            ItemStack item = new ItemStack(material, amount);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);

            return leftover.isEmpty(); // Return true if all items were added

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error adding player resources: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get Minecraft material for resource type
     */
    private Material getMaterialForResource(String resourceType) {
        switch (resourceType.toLowerCase()) {
            case "diamond": return Material.DIAMOND;
            case "gold": return Material.GOLD_INGOT;
            case "iron": return Material.IRON_INGOT;
            case "emerald": return Material.EMERALD;
            case "experience": return Material.EXPERIENCE_BOTTLE;
            default: return null;
        }
    }
}