package com.azoth.territory.permission;

import com.azoth.territory.model.Government;
import com.azoth.territory.model.GovernmentForm;

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
 * @param government derived from the nation's chosen form + role holders
 * @param memberGuildIds nation member guild ids
 */
public record AllianceBody(
        String id,
        String name,
        Government government,
        List<String> memberGuildIds
) {

    public AllianceBody {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(government, "government");
        Objects.requireNonNull(memberGuildIds, "memberGuildIds");
    }

    public GovernmentForm governmentForm() {
        return government.form();
    }

    public boolean containsGuild(String guildId) {
        return guildId != null && !guildId.isBlank() && memberGuildIds.contains(guildId.trim());
    }
}
