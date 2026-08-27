package dev.mintychochip.guilds.alliances;

import dev.mintychochip.territory.model.Government;
import dev.mintychochip.territory.model.GovernmentForm;

import java.util.List;
import java.util.Objects;

/**
 * An alliance (nation) as a governing entity — the DTO the guilds subsystem
 * materializes from its nation database records.
 * <p>
 * Replaces the former standalone {@code TerritoryAlliance} model: an alliance
 * governs through its member guilds (nation member guilds), not through a list
 * of territory ids.
 *
 * @param id stable alliance identifier
 * @param name display name
 * @param government derived from the nation's chosen form + role holders
 * @param memberGuildIds nation member guild ids
 */
public record Alliance(
        String id,
        String name,
        Government government,
        List<String> memberGuildIds
) {
    /** Creates an alliance snapshot after validating its required fields. */

    public Alliance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(government, "government");
        Objects.requireNonNull(memberGuildIds, "memberGuildIds");
    }
    /** Returns the alliance's government form.
     *
     * @return the alliance's government form
     */
    public GovernmentForm governmentForm() {
        return government.form();
    }
    /**
     * Whether this alliance contains the supplied guild.
     *
     * @param guildId guild identifier
     * @return whether the guild is a member
     */
    public boolean containsGuild(String guildId) {
        return guildId != null && !guildId.isBlank() && memberGuildIds.contains(guildId.trim());
    }
}
