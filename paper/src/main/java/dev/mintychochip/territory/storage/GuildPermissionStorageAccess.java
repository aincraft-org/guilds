package dev.mintychochip.territory.storage;

import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.GuildService;

import java.util.Objects;
import java.util.UUID;

/** Rank checks against the live guild roster. */
public final class GuildPermissionStorageAccess implements GuildStorageAccess {
    private final GuildService guilds;

    /**
     * Creates an adapter.
     *
     * @param guilds guild service
     */
    public GuildPermissionStorageAccess(GuildService guilds) {
        this.guilds = Objects.requireNonNull(guilds, "guilds");
    }

    @Override
    public boolean isResident(UUID playerId, String guildId) {
        return guild(guildId).map(guild -> guild.isResident(playerId)).orElse(false);
    }

    @Override
    public boolean canDeposit(UUID playerId, String guildId) {
        return isResident(playerId, guildId);
    }

    @Override
    public boolean canWithdraw(UUID playerId, String guildId) {
        return guild(guildId).map(guild -> guild.isMayor(playerId) || guild.isAssistant(playerId))
                .orElse(false);
    }

    private java.util.Optional<Guild> guild(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            return java.util.Optional.empty();
        }
        return guilds.getGuildById(guildId.trim());
    }
}
