package org.aincraft;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.storage.InviteRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Service for managing guild invitations.
 */
@Singleton
public class InviteService {

    private static final int MAX_PENDING_INVITES = 10;

    private final InviteRepository inviteRepository;
    private final GuildLifecycleService lifecycleService;
    private final GuildMemberService memberService;
    private final PermissionService permissionService;
    private final GuildsPlugin plugin;

    @Inject
    public InviteService(InviteRepository inviteRepository,
                         GuildLifecycleService lifecycleService,
                         GuildMemberService memberService,
                         PermissionService permissionService,
                         GuildsPlugin plugin) {
        this.inviteRepository = Objects.requireNonNull(inviteRepository, "Invite repository cannot be null");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "Lifecycle service cannot be null");
        this.memberService = Objects.requireNonNull(memberService, "Member service cannot be null");
        this.permissionService = Objects.requireNonNull(permissionService, "Permission service cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        startCleanupTask();
    }

    /**
     * Sends an invite to a player to join a guild.
     *
     * @param guildId the guild ID
     * @param inviterId the inviter's UUID
     * @param inviteeId the invitee's UUID
     * @return the result of the invite operation
     */
    public InviteResult sendInvite(UUID guildId, UUID inviterId, UUID inviteeId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(inviterId, "Inviter ID cannot be null");
        Objects.requireNonNull(inviteeId, "Invitee ID cannot be null");

        // 1. Check inviter has INVITE permission
        if (!permissionService.hasPermission(guildId, inviterId, GuildPermission.INVITE)) {
            return InviteResult.noPermission();
        }

        // 2. Check invitee exists
        OfflinePlayer invitee = Bukkit.getOfflinePlayer(inviteeId);
        if (!invitee.hasPlayedBefore() && !invitee.isOnline()) {
            return InviteResult.targetNotFound();
        }

        // 3. Check inviter in guild
        Guild guild = memberService.getPlayerGuild(inviterId);
        if (guild == null || !guild.getId().equals(guildId)) {
            return InviteResult.alreadyInGuild();
        }

        // 4. Check invitee not in ANY guild
        Guild inviteeGuild = memberService.getPlayerGuild(inviteeId);
        if (inviteeGuild != null) {
            return InviteResult.inviteeInGuild();
        }

        // 5. Check guild not full (preemptive)
        if (guild.getMemberCount() >= guild.getMaxMembers()) {
            return InviteResult.guildFull();
        }

        // 6. Check no active invite exists
        Optional<GuildInvite> existingInvite = inviteRepository.findActiveInvite(guildId, inviteeId);
        if (existingInvite.isPresent()) {
            // Delete if expired, otherwise return already invited
            if (existingInvite.get().isExpired()) {
                inviteRepository.delete(existingInvite.get().id());
            } else {
                return InviteResult.alreadyInvited();
            }
        }

        // 7. Check guild pending invites < 10
        int pendingCount = inviteRepository.countPendingInvites(guildId);
        if (pendingCount >= MAX_PENDING_INVITES) {
            return InviteResult.inviteLimitReached();
        }

        // 8. Create and save invite
        GuildInvite invite = GuildInvite.create(guildId, inviterId, inviteeId);
        inviteRepository.save(invite);

        return InviteResult.success();
    }

    /**
     * Accepts a guild invite.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @return the result of accepting the invite
     */
    public AcceptInviteResult acceptInvite(UUID guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        // 1. Find active invite
        Optional<GuildInvite> inviteOpt = inviteRepository.findActiveInvite(guildId, playerId);
        if (inviteOpt.isEmpty()) {
            return AcceptInviteResult.notFound();
        }

        GuildInvite invite = inviteOpt.get();

        // 2. Check not expired
        if (invite.isExpired()) {
            inviteRepository.delete(invite.id());
            return AcceptInviteResult.expired();
        }

        // 3. Check player not in guild
        Guild currentGuild = memberService.getPlayerGuild(playerId);
        if (currentGuild != null) {
            inviteRepository.delete(invite.id());
            return AcceptInviteResult.alreadyInGuild();
        }

        // 4. Check guild exists and not full
        Guild guild = lifecycleService.getGuildById(guildId);
        if (guild == null) {
            inviteRepository.delete(invite.id());
            return AcceptInviteResult.guildNotFound();
        }
        if (guild.getMemberCount() >= guild.getMaxMembers()) {
            inviteRepository.delete(invite.id());
            return AcceptInviteResult.guildFull();
        }

        // 5. Delete invite
        inviteRepository.delete(invite.id());

        // 6. Join guild
        boolean joined = memberService.joinGuild(guildId, playerId);
        if (!joined) {
            return AcceptInviteResult.failure("Failed to join guild");
        }

        return AcceptInviteResult.success();
    }

    /**
     * Declines a guild invite.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @return true if invite was declined, false if not found
     */
    public boolean declineInvite(UUID guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        Optional<GuildInvite> inviteOpt = inviteRepository.findActiveInvite(guildId, playerId);
        if (inviteOpt.isEmpty()) {
            return false;
        }

        inviteRepository.delete(inviteOpt.get().id());
        return true;
    }

    /**
     * Gets all non-expired invites received by a player.
     *
     * @param playerId the player UUID
     * @return list of invites
     */
    public List<GuildInvite> getReceivedInvites(UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        return inviteRepository.findByInviteeId(playerId).stream()
            .filter(invite -> !invite.isExpired())
            .collect(Collectors.toList());
    }

    /**
     * Gets all non-expired invites sent by a guild.
     *
     * @param guildId the guild ID
     * @return list of invites
     */
    public List<GuildInvite> getSentInvites(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        return inviteRepository.findByGuildId(guildId).stream()
            .filter(invite -> !invite.isExpired())
            .collect(Collectors.toList());
    }

    /**
     * Starts a background task to clean up expired invites.
     */
    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::cleanupExpiredInvites,
            20L * 60L,  // Delay 60 seconds
            20L * 60L   // Repeat every 60 seconds
        );
    }

    /**
     * Removes all expired invites from the database.
     */
    private void cleanupExpiredInvites() {
        try {
            inviteRepository.deleteExpired();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to clean up expired invites: " + e.getMessage());
        }
    }
}
