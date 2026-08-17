package dev.mintychochip.territory.storage;

import java.util.UUID;

/** Rank checks for guild item storage. */
public interface GuildStorageAccess {
    /**
     * Returns whether the player is a current resident of the guild.
     *
     * @param playerId actor
     * @param guildId guild
     * @return {@code true} when the player is a resident
     */
    boolean isResident(UUID playerId, String guildId);

    /**
     * Returns whether the player may deposit items. Default: any resident.
     *
     * @param playerId actor
     * @param guildId guild
     * @return {@code true} when deposit is allowed
     */
    boolean canDeposit(UUID playerId, String guildId);

    /**
     * Returns whether the player may withdraw items. Default: assistant or mayor.
     *
     * @param playerId actor
     * @param guildId guild
     * @return {@code true} when withdraw is allowed
     */
    boolean canWithdraw(UUID playerId, String guildId);
}
