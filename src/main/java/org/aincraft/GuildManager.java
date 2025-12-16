package org.aincraft;

import com.google.inject.Inject;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Guild management class that provides a simplified interface to guild operations.
 * This class manages the underlying GuildService and its dependencies.
 */
public class GuildManager {
    private final GuildService guildService;

    @Inject
    public GuildManager(GuildService guildService) {
        this.guildService = guildService;
    }

    /**
     * Creates a new guild with the specified parameters.
     *
     * @param name the guild name (cannot be null or empty)
     * @param description the guild description (can be null)
     * @param ownerId the UUID of the guild owner (cannot be null)
     * @return the newly created Guild
     * @throws IllegalStateException if guild name already exists or owner is already in a guild
     * @throws IllegalArgumentException if name or ownerId is null
     */
    public Guild createGuild(String name, String description, UUID ownerId) {
        Guild guild = guildService.createGuild(name, description, ownerId);
        if (guild == null) {
            throw new IllegalStateException("Guild creation failed. Name may already exist or owner may already be in a guild.");
        }
        return guild;
    }

    /**
     * Deletes a guild if the requester is the owner.
     *
     * @param guildId the ID of the guild to delete (cannot be null)
     * @param requesterId the UUID of the player requesting deletion (cannot be null)
     * @return true if the guild was deleted, false otherwise
     */
    public boolean deleteGuild(String guildId, UUID requesterId) {
        return guildService.deleteGuild(guildId, requesterId);
    }

    /**
     * Adds a player to a guild.
     *
     * @param guildId the ID of the guild to join (cannot be null)
     * @param playerId the UUID of the player joining (cannot be null)
     * @return true if the player joined successfully, false otherwise
     * @throws IllegalStateException if player is already in a guild or guild is full
     */
    public boolean joinGuild(String guildId, UUID playerId) {
        return guildService.joinGuild(guildId, playerId);
    }

    /**
     * Removes a player from their current guild.
     *
     * @param playerId the UUID of the player leaving (cannot be null)
     * @return true if the player left successfully, false otherwise
     */
    public boolean leaveGuild(UUID playerId) {
        Guild guild = guildService.getPlayerGuild(playerId);
        if (guild == null) {
            return false;
        }
        return guildService.leaveGuild(guild.getId(), playerId);
    }

    /**
     * Kicks a member from a guild. Only the guild owner can kick members.
     *
     * @param guildId the ID of the guild (cannot be null)
     * @param kickerId the UUID of the player attempting to kick (cannot be null)
     * @param targetId the UUID of the player to be kicked (cannot be null)
     * @return true if the member was kicked successfully, false otherwise
     */
    public boolean kickMember(String guildId, UUID kickerId, UUID targetId) {
        return guildService.kickMember(guildId, kickerId, targetId);
    }

    /**
     * Returns a list of all guilds.
     *
     * @return a list of all guilds
     */
    public List<Guild> getAllGuilds() {
        return guildService.listAllGuilds();
    }

    /**
     * Gets a guild by its ID.
     *
     * @param guildId the guild ID (cannot be null)
     * @return an Optional containing the Guild if found, empty otherwise
     */
    public Optional<Guild> getGuildById(String guildId) {
        return Optional.ofNullable(guildService.getGuildById(guildId));
    }

    /**
     * Gets the guild that a player is currently in.
     *
     * @param playerId the UUID of the player (cannot be null)
     * @return an Optional containing the Guild if found, empty otherwise
     */
    public Optional<Guild> getPlayerGuild(UUID playerId) {
        return Optional.ofNullable(guildService.getPlayerGuild(playerId));
    }

    /**
     * Claims the chunk the player is standing in for their guild.
     *
     * @param player the player claiming the chunk (cannot be null)
     * @return true if the chunk was claimed successfully, false otherwise
     */
    public boolean claimChunk(Player player) {
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            return false;
        }

        ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());
        return guildService.claimChunk(guild.getId(), player.getUniqueId(), chunk);
    }

    /**
     * Unclaims the chunk the player is standing in.
     *
     * @param player the player unclaiming the chunk (cannot be null)
     * @return true if the chunk was unclaimed successfully, false otherwise
     */
    public boolean unclaimChunk(Player player) {
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            return false;
        }

        ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());
        return guildService.unclaimChunk(guild.getId(), player.getUniqueId(), chunk);
    }

    /**
     * Kicks a member from a guild (overload that takes player UUIDs directly).
     *
     * @param kickerId the UUID of the kicker (cannot be null)
     * @param targetId the UUID of the player to kick (cannot be null)
     * @return true if the member was kicked successfully, false otherwise
     */
    public boolean kickMember(UUID kickerId, UUID targetId) {
        Guild guild = guildService.getPlayerGuild(kickerId);
        if (guild == null) {
            return false;
        }
        return guildService.kickMember(guild.getId(), kickerId, targetId);
    }
}