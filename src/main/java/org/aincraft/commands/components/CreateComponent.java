package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.TerritoryService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;

/**
 * Component for creating a new guild.
 */
public class CreateComponent implements GuildCommand {
    private final GuildLifecycleService lifecycleService;
    private final TerritoryService territoryService;
    private final GuildService guildService;

    @Inject
    public CreateComponent(GuildLifecycleService lifecycleService, TerritoryService territoryService, GuildService guildService) {
        this.lifecycleService = lifecycleService;
        this.territoryService = territoryService;
        this.guildService = guildService;
    }

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public String getPermission() {
        return "guilds.create";
    }

    @Override
    public String getUsage() {
        return "/g create <name> [description]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        if (args.length < 2) {
            Messages.send(player, MessageKey.ERROR_USAGE, getUsage());
            return false;
        }

        String name = args[1];
        String description = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : null;

        // Extract the chunk where the guild will be created
        org.aincraft.ChunkKey chunk = org.aincraft.ChunkKey.from(player.getLocation().getChunk());

        // VALIDATION PHASE: Validate chunk claim BEFORE creating guild
        // This ensures we don't create a guild in the database if chunk claim would fail
        org.aincraft.ClaimResult preValidation = validateChunkClaimForNewGuild(chunk, player.getUniqueId());
        if (!preValidation.isSuccess()) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION, preValidation.getReason());
            return true;
        }

        // CREATION PHASE: All validations passed, now create the guild
        Guild guild = lifecycleService.createGuild(name, description, player.getUniqueId());

        if (guild == null) {
            Messages.send(player, MessageKey.GUILD_NAME_TAKEN);
            return true;
        }

        Messages.send(player, MessageKey.GUILD_CREATED, guild.getName());

        // CLAIM PHASE: Guild created successfully, now claim the chunk
        // This should succeed given our pre-validation, but we still check the result
        org.aincraft.ClaimResult claimResult = claimInitialChunk(guild.getId(), player.getUniqueId(), chunk);
        if (claimResult.isSuccess()) {
            Messages.send(player, MessageKey.CLAIM_SUCCESS, chunk.x(), chunk.z());
        } else {
            // This is a fallback - should not happen after pre-validation
            // Log a warning as this indicates an unexpected state
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION, claimResult.getReason());
        }

        // SPAWN PHASE: Set spawn location to player's current location
        // Guild owner automatically has MANAGE_SPAWN permission
        Location playerLocation = player.getLocation();
        boolean spawnSet = guildService.setGuildSpawn(guild.getId(), player.getUniqueId(), playerLocation);
        if (spawnSet) {
            Messages.send(player, MessageKey.SPAWN_SET);
        } else {
            // This should not happen since guild was just created with a claimed chunk
            // But log a warning if spawn setting fails for any reason
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION, "Could not set guild spawn");
        }

        return true;
    }

    /**
     * Validates that a chunk can be claimed for a new guild.
     * Checks all validation rules that would fail during actual claim.
     *
     * @param chunk the chunk to validate
     * @param playerId the player attempting to create the guild
     * @return ClaimResult.success() if valid, or a failure reason if not
     */
    private org.aincraft.ClaimResult validateChunkClaimForNewGuild(org.aincraft.ChunkKey chunk, java.util.UUID playerId) {
        // Check if chunk is already claimed
        Guild chunkOwner = territoryService.getChunkOwner(chunk);
        if (chunkOwner != null) {
            return org.aincraft.ClaimResult.alreadyClaimed(chunkOwner.getName());
        }

        // For a new guild, check buffer distance to other guilds
        // Use a temporary UUID that won't match any existing guild
        java.util.UUID tempGuildId = java.util.UUID.randomUUID();
        org.aincraft.ClaimResult bufferCheck = territoryService.validateBufferDistance(chunk, tempGuildId);
        if (!bufferCheck.isSuccess()) {
            return bufferCheck;
        }

        return org.aincraft.ClaimResult.success();
    }

    /**
     * Claims the initial chunk for a newly created guild.
     * Should only be called after the guild has been successfully created.
     *
     * @param guildId the guild ID
     * @param playerId the player claiming the chunk
     * @param chunk the chunk to claim
     * @return the claim result
     */
    private org.aincraft.ClaimResult claimInitialChunk(java.util.UUID guildId, java.util.UUID playerId, org.aincraft.ChunkKey chunk) {
        return territoryService.claimChunk(guildId, playerId, chunk);
    }
}
