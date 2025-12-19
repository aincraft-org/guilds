package org.aincraft.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildService;

import java.util.Objects;
import java.util.UUID;

/**
 * Facade service for permission checks.
 * Delegates to GuildService for all operations.
 */
@Singleton
public class PermissionService {
    private final GuildService guildService;

    @Inject
    public PermissionService(GuildService guildService) {
        this.guildService = Objects.requireNonNull(guildService);
    }

    /**
     * Checks if a player has a specific permission in a guild.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @param permission the permission to check
     * @return true if the player has the permission
     */
    public boolean hasPermission(UUID guildId, UUID playerId, GuildPermission permission) {
        return guildService.hasPermission(guildId, playerId, permission);
    }
}
