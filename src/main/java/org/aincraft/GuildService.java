package org.aincraft;

import com.google.inject.Inject;
import org.aincraft.storage.GuildRepository;
import org.aincraft.storage.GuildMemberRepository;
import org.aincraft.storage.GuildRoleRepository;
import org.aincraft.storage.MemberRoleRepository;
import org.aincraft.storage.ChunkClaimRepository;
import org.aincraft.storage.PlayerGuildMapping;
import java.util.*;

/**
 * Service layer for guild operations using dependency injection.
 * Single Responsibility: Guild business logic and operation coordination.
 * Open/Closed: Depends on abstractions, not implementations.
 * Dependency Inversion: Injected dependencies via constructor.
 */
public class GuildService {
    private final GuildRepository guildRepository;
    private final PlayerGuildMapping playerGuildMapping;
    private final GuildMemberRepository memberRepository;
    private final GuildRoleRepository roleRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final ChunkClaimRepository chunkClaimRepository;

    @Inject
    public GuildService(GuildRepository guildRepository, PlayerGuildMapping playerGuildMapping,
                        GuildMemberRepository memberRepository, GuildRoleRepository roleRepository,
                        MemberRoleRepository memberRoleRepository, ChunkClaimRepository chunkClaimRepository) {
        this.guildRepository = Objects.requireNonNull(guildRepository, "Guild repository cannot be null");
        this.playerGuildMapping = Objects.requireNonNull(playerGuildMapping, "Player guild mapping cannot be null");
        this.memberRepository = Objects.requireNonNull(memberRepository, "Member repository cannot be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "Role repository cannot be null");
        this.memberRoleRepository = Objects.requireNonNull(memberRoleRepository, "Member role repository cannot be null");
        this.chunkClaimRepository = Objects.requireNonNull(chunkClaimRepository, "Chunk claim repository cannot be null");
    }

    /**
     * Creates a new guild with the specified parameters.
     *
     * @param name the guild name (cannot be null or empty)
     * @param description the guild description (can be null)
     * @param ownerId the UUID of the guild owner (cannot be null)
     * @return the newly created Guild, or null if creation failed
     * @throws IllegalArgumentException if name or ownerId is null
     */
    public Guild createGuild(String name, String description, UUID ownerId) {
        Objects.requireNonNull(name, "Guild name cannot be null");
        Objects.requireNonNull(ownerId, "Owner ID cannot be null");

        if (guildRepository.findByName(name).isPresent()) {
            return null;
        }

        if (playerGuildMapping.isPlayerInGuild(ownerId)) {
            return null;
        }

        Guild guild = new Guild(name, description, ownerId);
        guildRepository.save(guild);
        playerGuildMapping.addPlayerToGuild(ownerId, guild.getId());
        memberRepository.addMember(guild.getId(), ownerId, MemberPermissions.all());

        // Create default "Member" role and assign to owner
        GuildRole defaultRole = GuildRole.createDefault(guild.getId());
        roleRepository.save(defaultRole);
        memberRoleRepository.assignRole(guild.getId(), ownerId, defaultRole.getId());

        return guild;
    }

    /**
     * Deletes a guild if the requester is the owner.
     *
     * @param guildId the ID of the guild to delete (cannot be null)
     * @param requesterId the UUID of the player requesting deletion (cannot be null)
     * @return true if the guild was deleted, false otherwise
     * @throws IllegalArgumentException if guildId or requesterId is null
     */
    public boolean deleteGuild(String guildId, UUID requesterId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return false;
        }

        Guild guild = guildOpt.get();
        if (!guild.deleteGuild(requesterId)) {
            return false;
        }

        for (UUID memberId : guild.getMembers()) {
            playerGuildMapping.removePlayerFromGuild(memberId);
        }
        memberRepository.removeAllMembers(guildId);
        memberRoleRepository.removeAllByGuild(guildId);
        roleRepository.deleteAllByGuild(guildId);
        chunkClaimRepository.unclaimAll(guildId);

        guildRepository.delete(guildId);
        return true;
    }

    /**
     * Adds a player to a guild.
     *
     * @param guildId the ID of the guild to join (cannot be null)
     * @param playerId the UUID of the player joining (cannot be null)
     * @return true if the player joined successfully, false otherwise
     * @throws IllegalArgumentException if guildId or playerId is null
     */
    public boolean joinGuild(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        if (playerGuildMapping.isPlayerInGuild(playerId)) {
            return false;
        }

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return false;
        }

        Guild guild = guildOpt.get();
        if (guild.joinGuild(playerId)) {
            playerGuildMapping.addPlayerToGuild(playerId, guildId);
            memberRepository.addMember(guildId, playerId, MemberPermissions.getDefault());

            // Assign default role to new member
            roleRepository.findByGuildAndName(guildId, GuildRole.DEFAULT_ROLE_NAME)
                    .ifPresent(role -> memberRoleRepository.assignRole(guildId, playerId, role.getId()));

            guildRepository.save(guild);
            return true;
        }

        return false;
    }

    /**
     * Removes a player from a guild.
     *
     * @param guildId the ID of the guild to leave (cannot be null)
     * @param playerId the UUID of the player leaving (cannot be null)
     * @return true if the player left successfully, false otherwise
     * @throws IllegalArgumentException if guildId or playerId is null
     */
    public boolean leaveGuild(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return false;
        }

        Guild guild = guildOpt.get();
        if (guild.leaveGuild(playerId)) {
            playerGuildMapping.removePlayerFromGuild(playerId);
            memberRepository.removeMember(guildId, playerId);
            memberRoleRepository.removeAllMemberRoles(guildId, playerId);
            guildRepository.save(guild);
            return true;
        }

        return false;
    }

    /**
     * Kicks a member from a guild. Only the guild owner can kick members.
     *
     * @param guildId the ID of the guild (cannot be null)
     * @param kickerId the UUID of the player attempting to kick (cannot be null)
     * @param targetId the UUID of the player to be kicked (cannot be null)
     * @return true if the member was kicked successfully, false otherwise
     * @throws IllegalArgumentException if guildId, kickerId, or targetId is null
     */
    public boolean kickMember(String guildId, UUID kickerId, UUID targetId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(kickerId, "Kicker ID cannot be null");
        Objects.requireNonNull(targetId, "Target ID cannot be null");

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return false;
        }

        Guild guild = guildOpt.get();
        if (guild.kickMember(kickerId, targetId)) {
            playerGuildMapping.removePlayerFromGuild(targetId);
            memberRepository.removeMember(guildId, targetId);
            memberRoleRepository.removeAllMemberRoles(guildId, targetId);
            guildRepository.save(guild);
            return true;
        }

        return false;
    }

    /**
     * Returns a list of all guilds.
     *
     * @return an unmodifiable list of all guilds
     */
    public List<Guild> listAllGuilds() {
        return guildRepository.findAll();
    }

    /**
     * Gets a guild by its ID.
     *
     * @param guildId the guild ID (cannot be null)
     * @return the Guild, or null if not found
     * @throws IllegalArgumentException if guildId is null
     */
    public Guild getGuildById(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return guildRepository.findById(guildId).orElse(null);
    }

    /**
     * Gets a guild by its name (case-insensitive).
     *
     * @param name the guild name (cannot be null)
     * @return the Guild, or null if not found
     * @throws IllegalArgumentException if name is null
     */
    public Guild getGuildByName(String name) {
        Objects.requireNonNull(name, "Guild name cannot be null");
        return guildRepository.findByName(name).orElse(null);
    }

    /**
     * Gets the guild that a player is currently in.
     *
     * @param playerId the UUID of the player (cannot be null)
     * @return the Guild the player is in, or null if not in any guild
     * @throws IllegalArgumentException if playerId is null
     */
    public Guild getPlayerGuild(UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Optional<String> guildIdOpt = playerGuildMapping.getPlayerGuildId(playerId);
        return guildIdOpt.flatMap(guildRepository::findById).orElse(null);
    }

    /**
     * Checks if a player has a specific permission in a guild.
     * Permissions are computed by OR-ing all assigned role permissions.
     * Guild owners always have all permissions.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @param permission the permission to check
     * @return true if the player has the permission
     */
    public boolean hasPermission(String guildId, UUID playerId, GuildPermission permission) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(permission, "Permission cannot be null");

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return false;
        }

        Guild guild = guildOpt.get();
        if (guild.isOwner(playerId)) {
            return true;
        }

        // Compute effective permissions from all assigned roles
        int effectivePermissions = computeEffectivePermissions(guildId, playerId);
        return (effectivePermissions & permission.getBit()) != 0;
    }

    /**
     * Computes effective permissions by OR-ing all assigned role permissions.
     */
    private int computeEffectivePermissions(String guildId, UUID playerId) {
        List<String> roleIds = memberRoleRepository.getMemberRoleIds(guildId, playerId);
        int permissions = 0;
        for (String roleId : roleIds) {
            Optional<GuildRole> role = roleRepository.findById(roleId);
            if (role.isPresent()) {
                permissions |= role.get().getPermissions();
            }
        }
        return permissions;
    }

    /**
     * Gets the permissions for a member in a guild.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @return the member's permissions, or null if not found
     */
    public MemberPermissions getMemberPermissions(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        return memberRepository.getPermissions(guildId, playerId).orElse(null);
    }

    /**
     * Sets the permissions for a member in a guild.
     * Only the guild owner can modify permissions.
     *
     * @param guildId the guild ID
     * @param requesterId the player requesting the change (must be owner)
     * @param targetId the player whose permissions to change
     * @param permissions the new permissions
     * @return true if permissions were updated
     */
    public boolean setMemberPermissions(String guildId, UUID requesterId, UUID targetId,
                                        MemberPermissions permissions) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");
        Objects.requireNonNull(targetId, "Target ID cannot be null");
        Objects.requireNonNull(permissions, "Permissions cannot be null");

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return false;
        }

        Guild guild = guildOpt.get();
        if (!guild.isOwner(requesterId)) {
            return false;
        }

        if (!guild.isMember(targetId)) {
            return false;
        }

        memberRepository.setPermissions(guildId, targetId, permissions);
        return true;
    }

    // ==================== Chunk Claim Methods ====================

    /**
     * Claims a chunk for a guild.
     * Requires the player to have CLAIM permission.
     *
     * @param guildId the guild ID
     * @param playerId the player claiming the chunk
     * @param chunk the chunk to claim
     * @return true if claimed successfully, false if no permission or already claimed
     */
    public boolean claimChunk(String guildId, UUID playerId, ChunkKey chunk) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(chunk, "Chunk cannot be null");

        if (!hasPermission(guildId, playerId, GuildPermission.CLAIM)) {
            return false;
        }

        boolean claimed = chunkClaimRepository.claim(chunk, guildId, playerId);
        if (!claimed) {
            return false;
        }

        // Auto-set homeblock if this is first claim
        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isPresent()) {
            Guild guild = guildOpt.get();
            if (!guild.hasHomeblock()) {
                guild.setHomeblock(chunk);
                guildRepository.save(guild);
            }
        }

        return true;
    }

    /**
     * Unclaims a chunk from a guild.
     * Requires the player to have UNCLAIM permission.
     *
     * @param guildId the guild ID
     * @param playerId the player unclaiming the chunk
     * @param chunk the chunk to unclaim
     * @return true if unclaimed successfully, false if no permission or not owned by guild
     */
    public boolean unclaimChunk(String guildId, UUID playerId, ChunkKey chunk) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(chunk, "Chunk cannot be null");

        if (!hasPermission(guildId, playerId, GuildPermission.UNCLAIM)) {
            return false;
        }

        // Prevent unclaiming homeblock
        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isPresent()) {
            Guild guild = guildOpt.get();
            if (guild.hasHomeblock() && chunk.equals(guild.getHomeblock())) {
                return false; // Cannot unclaim homeblock
            }
        }

        return chunkClaimRepository.unclaim(chunk, guildId);
    }

    /**
     * Unclaims all chunks owned by a guild.
     * Requires the player to have UNCLAIM permission.
     *
     * @param guildId the guild ID
     * @param playerId the player unclaiming all chunks
     * @return true if unclaimed successfully, false if no permission
     */
    public boolean unclaimAllChunks(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        if (!hasPermission(guildId, playerId, GuildPermission.UNCLAIM)) {
            return false;
        }

        chunkClaimRepository.unclaimAll(guildId);
        return true;
    }

    /**
     * Gets the guild that owns a chunk.
     *
     * @param chunk the chunk to check
     * @return the guild if claimed, null otherwise
     */
    public Guild getChunkOwner(ChunkKey chunk) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");

        return chunkClaimRepository.getOwner(chunk)
                .flatMap(guildRepository::findById)
                .orElse(null);
    }

    /**
     * Gets all chunks claimed by a guild.
     *
     * @param guildId the guild ID
     * @return list of chunk keys
     */
    public List<ChunkKey> getGuildChunks(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return chunkClaimRepository.getGuildChunks(guildId);
    }

    /**
     * Gets the number of chunks claimed by a guild.
     *
     * @param guildId the guild ID
     * @return the chunk count
     */
    public int getGuildChunkCount(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return chunkClaimRepository.getChunkCount(guildId);
    }

    // ==================== Role Management Methods ====================

    /**
     * Creates a new role for a guild.
     * Requires MANAGE_ROLES permission.
     *
     * @param guildId the guild ID
     * @param requesterId the player creating the role
     * @param name the role name
     * @param permissions the permission bitfield
     * @return the created role, or null if failed
     */
    public GuildRole createRole(String guildId, UUID requesterId, String name, int permissions) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (!hasPermission(guildId, requesterId, GuildPermission.MANAGE_ROLES)) {
            return null;
        }

        if (roleRepository.findByGuildAndName(guildId, name).isPresent()) {
            return null; // Role with this name already exists
        }

        GuildRole role = new GuildRole(guildId, name, permissions);
        roleRepository.save(role);
        return role;
    }

    /**
     * Deletes a role from a guild.
     * Requires MANAGE_ROLES permission.
     *
     * @param guildId the guild ID
     * @param requesterId the player deleting the role
     * @param roleId the role ID to delete
     * @return true if deleted successfully
     */
    public boolean deleteRole(String guildId, UUID requesterId, String roleId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        if (!hasPermission(guildId, requesterId, GuildPermission.MANAGE_ROLES)) {
            return false;
        }

        Optional<GuildRole> roleOpt = roleRepository.findById(roleId);
        if (roleOpt.isEmpty() || !roleOpt.get().getGuildId().equals(guildId)) {
            return false;
        }

        memberRoleRepository.removeAllByRole(roleId);
        roleRepository.delete(roleId);
        return true;
    }

    /**
     * Updates a role's permissions.
     * Requires MANAGE_ROLES permission.
     *
     * @param guildId the guild ID
     * @param requesterId the player updating the role
     * @param roleId the role ID
     * @param permissions the new permission bitfield
     * @return true if updated successfully
     */
    public boolean updateRolePermissions(String guildId, UUID requesterId, String roleId, int permissions) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        if (!hasPermission(guildId, requesterId, GuildPermission.MANAGE_ROLES)) {
            return false;
        }

        Optional<GuildRole> roleOpt = roleRepository.findById(roleId);
        if (roleOpt.isEmpty() || !roleOpt.get().getGuildId().equals(guildId)) {
            return false;
        }

        GuildRole role = roleOpt.get();
        role.setPermissions(permissions);
        roleRepository.save(role);
        return true;
    }

    /**
     * Renames a role.
     * Requires MANAGE_ROLES permission.
     *
     * @param guildId the guild ID
     * @param requesterId the player renaming the role
     * @param roleId the role ID
     * @param newName the new role name
     * @return true if renamed successfully
     */
    public boolean renameRole(String guildId, UUID requesterId, String roleId, String newName) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");
        Objects.requireNonNull(newName, "New name cannot be null");

        if (!hasPermission(guildId, requesterId, GuildPermission.MANAGE_ROLES)) {
            return false;
        }

        Optional<GuildRole> roleOpt = roleRepository.findById(roleId);
        if (roleOpt.isEmpty() || !roleOpt.get().getGuildId().equals(guildId)) {
            return false;
        }

        // Check if new name already exists
        if (roleRepository.findByGuildAndName(guildId, newName).isPresent()) {
            return false;
        }

        GuildRole role = roleOpt.get();
        role.setName(newName);
        roleRepository.save(role);
        return true;
    }

    /**
     * Gets all roles for a guild.
     *
     * @param guildId the guild ID
     * @return list of roles
     */
    public List<GuildRole> getGuildRoles(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return roleRepository.findByGuildId(guildId);
    }

    /**
     * Gets a role by name in a guild.
     *
     * @param guildId the guild ID
     * @param name the role name
     * @return the role, or null if not found
     */
    public GuildRole getRoleByName(String guildId, String name) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        return roleRepository.findByGuildAndName(guildId, name).orElse(null);
    }

    /**
     * Gets a role by ID.
     *
     * @param roleId the role ID
     * @return the role, or null if not found
     */
    public GuildRole getRoleById(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");
        return roleRepository.findById(roleId).orElse(null);
    }

    // ==================== Role Assignment Methods ====================

    /**
     * Assigns a role to a member.
     * Requires MANAGE_ROLES permission.
     *
     * @param guildId the guild ID
     * @param requesterId the player assigning the role
     * @param targetId the member to assign the role to
     * @param roleId the role ID
     * @return true if assigned successfully
     */
    public boolean assignRole(String guildId, UUID requesterId, UUID targetId, String roleId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");
        Objects.requireNonNull(targetId, "Target ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        if (!hasPermission(guildId, requesterId, GuildPermission.MANAGE_ROLES)) {
            return false;
        }

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty() || !guildOpt.get().isMember(targetId)) {
            return false;
        }

        Optional<GuildRole> roleOpt = roleRepository.findById(roleId);
        if (roleOpt.isEmpty() || !roleOpt.get().getGuildId().equals(guildId)) {
            return false;
        }

        memberRoleRepository.assignRole(guildId, targetId, roleId);
        return true;
    }

    /**
     * Unassigns a role from a member.
     * Requires MANAGE_ROLES permission.
     *
     * @param guildId the guild ID
     * @param requesterId the player unassigning the role
     * @param targetId the member to unassign the role from
     * @param roleId the role ID
     * @return true if unassigned successfully
     */
    public boolean unassignRole(String guildId, UUID requesterId, UUID targetId, String roleId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");
        Objects.requireNonNull(targetId, "Target ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        if (!hasPermission(guildId, requesterId, GuildPermission.MANAGE_ROLES)) {
            return false;
        }

        memberRoleRepository.unassignRole(guildId, targetId, roleId);
        return true;
    }

    /**
     * Gets all roles assigned to a member.
     *
     * @param guildId the guild ID
     * @param playerId the member UUID
     * @return list of roles
     */
    public List<GuildRole> getMemberRoles(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        List<String> roleIds = memberRoleRepository.getMemberRoleIds(guildId, playerId);
        List<GuildRole> roles = new ArrayList<>();
        for (String roleId : roleIds) {
            roleRepository.findById(roleId).ifPresent(roles::add);
        }
        return roles;
    }

    /**
     * Gets effective permissions for a member (OR of all assigned role permissions).
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @return the effective permissions as MemberPermissions
     */
    public MemberPermissions getEffectivePermissions(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isPresent() && guildOpt.get().isOwner(playerId)) {
            return MemberPermissions.all();
        }

        return MemberPermissions.fromBitfield(computeEffectivePermissions(guildId, playerId));
    }

    // ==================== Spawn Location Methods ====================

    /**
     * Sets the guild spawn location.
     * Requires MANAGE_SPAWN permission and guild must have a homeblock.
     * If location is outside homeblock, it will be automatically relocated to a safe spot within homeblock.
     *
     * @param guildId the guild ID
     * @param playerId the player setting the spawn
     * @param location the location to set as spawn
     * @return true if spawn was set successfully, false otherwise
     */
    public boolean setGuildSpawn(String guildId, UUID playerId, org.bukkit.Location location) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(location, "Location cannot be null");

        if (!hasPermission(guildId, playerId, GuildPermission.MANAGE_SPAWN)) {
            return false;
        }

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return false;
        }

        Guild guild = guildOpt.get();

        // Guild must have homeblock
        if (!guild.hasHomeblock()) {
            return false;
        }

        // Adjust location to homeblock if needed
        org.bukkit.Location finalLocation = location;
        if (!guild.isInHomeblock(location)) {
            finalLocation = findSafeLocationInHomeblock(guild, location);
            if (finalLocation == null) {
                return false; // Could not find safe location
            }
        }

        guild.setSpawn(finalLocation);
        guildRepository.save(guild);
        return true;
    }

    /**
     * Clears the guild spawn location.
     * Requires MANAGE_SPAWN permission.
     *
     * @param guildId the guild ID
     * @param playerId the player clearing the spawn
     * @return true if spawn was cleared successfully, false otherwise
     */
    public boolean clearGuildSpawn(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        if (!hasPermission(guildId, playerId, GuildPermission.MANAGE_SPAWN)) {
            return false;
        }

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return false;
        }

        Guild guild = guildOpt.get();
        guild.clearSpawn();
        guildRepository.save(guild);
        return true;
    }

    /**
     * Gets the guild spawn location as a Bukkit Location.
     *
     * @param guildId the guild ID
     * @return the spawn location, or null if no spawn is set or world is not loaded
     */
    public org.bukkit.Location getGuildSpawnLocation(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        Guild guild = getGuildById(guildId);
        if (guild == null || !guild.hasSpawn()) {
            return null;
        }

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(guild.getSpawnWorld());
        if (world == null) {
            return null;
        }

        org.bukkit.Location location = new org.bukkit.Location(
            world,
            guild.getSpawnX(),
            guild.getSpawnY(),
            guild.getSpawnZ(),
            guild.getSpawnYaw(),
            guild.getSpawnPitch()
        );
        return location;
    }

    /**
     * Finds a safe location within a guild's homeblock for spawn.
     * Returns a location above the ground with air blocks above.
     *
     * @param guild the guild
     * @param preferredLocation the preferred location (ignored if outside homeblock)
     * @return a safe location in the homeblock, or null if none found
     */
    private org.bukkit.Location findSafeLocationInHomeblock(Guild guild, org.bukkit.Location preferredLocation) {
        if (!guild.hasHomeblock()) {
            return null;
        }

        ChunkKey homeblock = guild.getHomeblock();
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(homeblock.world());
        if (world == null) {
            return null;
        }

        // Try the center of the homeblock first
        int centerX = homeblock.x() * 16 + 8;
        int centerZ = homeblock.z() * 16 + 8;

        // Find safe Y coordinate (top of block with 2 air blocks above)
        for (int y = 255; y >= 0; y--) {
            org.bukkit.block.Block block = world.getBlockAt(centerX, y, centerZ);
            if (!block.getType().isAir() && !block.isLiquid()) {
                org.bukkit.Location safe = new org.bukkit.Location(world, centerX + 0.5, y + 2, centerZ + 0.5);
                org.bukkit.block.Block above1 = world.getBlockAt(centerX, y + 1, centerZ);
                org.bukkit.block.Block above2 = world.getBlockAt(centerX, y + 2, centerZ);
                if (above1.getType().isAir() && above2.getType().isAir()) {
                    return safe;
                }
            }
        }

        return null;
    }

    /**
     * Saves a guild to persistent storage.
     *
     * @param guild the guild to save
     */
    public void save(Guild guild) {
        Objects.requireNonNull(guild, "Guild cannot be null");
        guildRepository.save(guild);
    }
}
