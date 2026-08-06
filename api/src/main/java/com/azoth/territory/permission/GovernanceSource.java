package com.azoth.territory.permission;

import java.util.List;
import java.util.Optional;

/**
 * Source of governing-entity snapshots, implemented by the guilds subsystem
 * (towns as guilds, nations as alliances).
 * <p>
 * The territory side resolves governance exclusively through this interface —
 * there is no parallel in-memory guild/alliance world anymore.
 * Pure domain — no Bukkit.
 */
public interface GovernanceSource {

    /**
     * Guild (town) snapshot by guild id (town id).
     */
    Optional<GuildBody> guild(String guildId);

    /**
     * Guilds that list {@code holderId} as a member (stable id order).
     */
    List<GuildBody> guildsForMember(String holderId);

    /**
     * First alliance (nation) that contains the guild, if any.
     */
    Optional<AllianceBody> allianceContainingGuild(String guildId);

    /**
     * All guilds.
     */
    List<GuildBody> allGuilds();

    /**
     * All alliances.
     */
    List<AllianceBody> allAlliances();

    /**
     * Empty source: no guilds, no alliances. Used when the guilds subsystem
     * failed to start — everything resolves to territory-local government.
     */
    static GovernanceSource none() {
        return new GovernanceSource() {
            @Override
            public Optional<GuildBody> guild(String guildId) {
                return Optional.empty();
            }

            @Override
            public List<GuildBody> guildsForMember(String holderId) {
                return List.of();
            }

            @Override
            public Optional<AllianceBody> allianceContainingGuild(String guildId) {
                return Optional.empty();
            }

            @Override
            public List<GuildBody> allGuilds() {
                return List.of();
            }

            @Override
            public List<AllianceBody> allAlliances() {
                return List.of();
            }
        };
    }
}
