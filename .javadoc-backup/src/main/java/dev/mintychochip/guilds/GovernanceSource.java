package dev.mintychochip.guilds;
import dev.mintychochip.guilds.alliances.Alliance;

import java.util.List;
import java.util.Optional;

/**
 * Source of governing-entity snapshots, implemented by the guilds subsystem
 * (guilds as guilds, nations as alliances).
 * <p>
 * The territory side resolves governance exclusively through this interface —
 * there is no parallel in-memory guild/alliance world anymore.
 * Pure domain — no Bukkit.
 */
public interface GovernanceSource {

    /**
     * Finds a guild snapshot by identifier.
     *
     * @param guildId guild identifier
     * @return matching guild, if present
     */
    Optional<Guild> guild(String guildId);

    /**
     * Finds guilds listing a member.
     *
     * @param holderId member identifier
     * @return guilds in stable identifier order
     */
    List<Guild> guildsForMember(String holderId);

    /**
     * Finds the first alliance containing a guild.
     *
     * @param guildId guild identifier
     * @return containing alliance, if present
     */
    Optional<Alliance> allianceContainingGuild(String guildId);

    /**
     * Returns all guild snapshots.
     *
     * @return all guild snapshots
     */
    List<Guild> allGuilds();

    /**
     * Returns all alliance snapshots.
     *
     * @return all alliance snapshots
     */
    List<Alliance> allAlliances();

    /**
     * Returns an empty source with no guilds or alliances.
     *
     * @return an empty governance source
     */
    static GovernanceSource none() {
        return new GovernanceSource() {
            @Override
            public Optional<Guild> guild(String guildId) {
                return Optional.empty();
            }

            @Override
            public List<Guild> guildsForMember(String holderId) {
                return List.of();
            }

            @Override
            public Optional<Alliance> allianceContainingGuild(String guildId) {
                return Optional.empty();
            }

            @Override
            public List<Guild> allGuilds() {
                return List.of();
            }

            @Override
            public List<Alliance> allAlliances() {
                return List.of();
            }
        };
    }
}
