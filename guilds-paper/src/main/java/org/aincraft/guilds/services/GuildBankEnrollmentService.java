package org.aincraft.guilds.services;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Persistent enrollment and authorization state for guild-bank players. */
public interface GuildBankEnrollmentService {
    CompletionStage<EnrollmentResult> open(UUID playerUuid, String guildId);

    CompletionStage<Boolean> isEnrolled(UUID playerUuid, String guildId);

    CompletionStage<Boolean> deactivateForPlayerGuild(UUID playerUuid, String guildId);

    CompletionStage<Integer> deactivateForGuild(String guildId);

    enum EnrollmentResult {
        OPENED,
        ALREADY_OPEN,
        NOT_CURRENT_MEMBER,
        GUILD_NOT_FOUND,
        PLAYER_NOT_FOUND,
        FAILED
    }
}
