package org.aincraft.service;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.GuildRole;
import org.aincraft.MemberPermissions;
import org.aincraft.project.storage.GuildProjectPoolRepository;
import org.aincraft.storage.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing guild lifecycle operations (creation, deletion, queries).
 * Single Responsibility: Guild CRUD and high-level queries.
 */
public class GuildLifecycleService {
    private final GuildRepository guildRepository;
    private final PlayerGuildMapping playerGuildMapping;
    private final GuildMemberRepository memberRepository;
    private final GuildRoleRepository roleRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final ChunkClaimRepository chunkClaimRepository;
    private final GuildRelationshipRepository relationshipRepository;
    private final InviteRepository inviteRepository;
    private final GuildProjectPoolRepository poolRepository;

    @Inject
    public GuildLifecycleService(GuildRepository guildRepository,
                                 PlayerGuildMapping playerGuildMapping,
                                 GuildMemberRepository memberRepository,
                                 GuildRoleRepository roleRepository,
                                 MemberRoleRepository memberRoleRepository,
                                 ChunkClaimRepository chunkClaimRepository,
                                 GuildRelationshipRepository relationshipRepository,
                                 InviteRepository inviteRepository,
                                 GuildProjectPoolRepository poolRepository) {
        this.guildRepository = Objects.requireNonNull(guildRepository);
        this.playerGuildMapping = Objects.requireNonNull(playerGuildMapping);
        this.memberRepository = Objects.requireNonNull(memberRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.memberRoleRepository = Objects.requireNonNull(memberRoleRepository);
        this.chunkClaimRepository = Objects.requireNonNull(chunkClaimRepository);
        this.relationshipRepository = Objects.requireNonNull(relationshipRepository);
        this.inviteRepository = Objects.requireNonNull(inviteRepository);
        this.poolRepository = Objects.requireNonNull(poolRepository);
    }

    /**
     * Creates a new guild.
     *
     * @param name the guild name
     * @param description the guild description
     * @param ownerId the UUID of the guild owner
     * @return the newly created Guild, or null if creation failed
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

        Optional<Guild> guildOpt = Guild.create(name, description, ownerId);
        if (guildOpt.isEmpty()) {
            return null;
        }

        Guild guild = guildOpt.get();
        guildRepository.save(guild);
        playerGuildMapping.addPlayerToGuild(ownerId, guild.getId());
        memberRepository.addMember(guild.getId(), ownerId, MemberPermissions.all());

        // Create default "Member" role and assign to owner
        GuildRole defaultRole = new GuildRole(guild.getId(), GuildRole.DEFAULT_ROLE_NAME,
                                              org.aincraft.GuildPermission.defaultPermissions(), 0, ownerId);
        roleRepository.save(defaultRole);
        memberRoleRepository.assignRole(guild.getId(), ownerId, defaultRole.getId());

        // Initialize guild creation timestamp for project pool 24h refresh cycle
        poolRepository.setGuildCreatedAt(guild.getId(), System.currentTimeMillis());

        return guild;
    }

    /**
     * Deletes a guild if the requester is the owner.
     * Notifies all guild members (including the owner) before deletion.
     *
     * @param guildId the guild ID
     * @param requesterId the UUID of the player requesting deletion
     * @return true if the guild was deleted
     */
    public boolean deleteGuild(UUID guildId, UUID requesterId) {
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

        // Notify all members before database deletion to ensure guild data is available
        notifyMembersOfDisband(guild);

        for (UUID memberId : guild.getMembers()) {
            playerGuildMapping.removePlayerFromGuild(memberId);
        }
        memberRepository.removeAllMembers(guildId);
        memberRoleRepository.removeAllByGuild(guildId);
        roleRepository.deleteAllByGuild(guildId);
        chunkClaimRepository.unclaimAll(guildId);
        relationshipRepository.deleteAllByGuild(guildId);
        inviteRepository.deleteByGuildId(guildId);

        guildRepository.delete(guildId);
        return true;
    }

    /**
     * Notifies all guild members that their guild has been disbanded.
     *
     * @param guild the guild being disbanded
     */
    private void notifyMembersOfDisband(Guild guild) {
        MiniMessage mm = MiniMessage.miniMessage();
        String disbandMessage = "<red>✗ Guild '<gold>" + guild.getName() + "</gold>' has been disbanded by the owner</red>";

        for (UUID memberId : guild.getMembers()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                player.sendMessage(mm.deserialize(disbandMessage));
            }
        }
    }

    /**
     * Gets all guilds.
     *
     * @return list of all guilds
     */
    public List<Guild> listAllGuilds() {
        return guildRepository.findAll();
    }

    /**
     * Gets a guild by its ID.
     *
     * @param guildId the guild ID
     * @return the Guild, or null if not found
     */
    public Guild getGuildById(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return guildRepository.findById(guildId).orElse(null);
    }

    /**
     * Gets a guild by its name (case-insensitive).
     *
     * @param name the guild name
     * @return the Guild, or null if not found
     */
    public Guild getGuildByName(String name) {
        Objects.requireNonNull(name, "Guild name cannot be null");
        return guildRepository.findByName(name).orElse(null);
    }

    /**
     * Updates a guild's maximum capacity for members and chunks.
     * Used by progression system to apply level-up rewards.
     *
     * @param guildId the guild ID
     * @param maxMembers the new maximum members
     * @param maxChunks the new maximum chunks
     */
    public void updateGuildCapacities(UUID guildId, int maxMembers, int maxChunks) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return;
        }

        Guild guild = guildOpt.get();
        guild.setMaxMembers(maxMembers);
        guild.setMaxChunks(maxChunks);
        guildRepository.save(guild);
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
