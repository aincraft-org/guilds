package dev.mintychochip.guilds.services;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Persistent enrollment and authorization state for guild-bank players. */
public interface GuildBankEnrollmentService {
    /**
     * Performs the open operation.
     * @param playerUuid the player uuid
     * @param guildId the guild id
     * @return the result
     */
    CompletionStage<EnrollmentResult> open(UUID playerUuid, String guildId);

    /**
     * Returns whether enrolled.
     * @param playerUuid the player uuid
     * @param guildId the guild id
     * @return the result
     */
    CompletionStage<Boolean> isEnrolled(UUID playerUuid, String guildId);

    /**
     * Performs the deactivate for player guild operation.
     * @param playerUuid the player uuid
     * @param guildId the guild id
     * @return the result
     */
    CompletionStage<Boolean> deactivateForPlayerGuild(UUID playerUuid, String guildId);

    /**
     * Performs the deactivate for guild operation.
     * @param guildId the guild id
     * @return the result
     */
    CompletionStage<Integer> deactivateForGuild(String guildId);

    /** Defines the values of enrollment result. */
    enum EnrollmentResult {
        /** The opened constant. */
        OPENED,
        /** The already open constant. */
        ALREADY_OPEN,
        /** The not current member constant. */
        NOT_CURRENT_MEMBER,
        /** The guild not found constant. */
        GUILD_NOT_FOUND,
        /** The player not found constant. */
        PLAYER_NOT_FOUND,
        /** The failed constant. */
        FAILED
    }
}
