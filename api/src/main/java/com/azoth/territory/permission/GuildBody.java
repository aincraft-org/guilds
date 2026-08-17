package com.azoth.territory.permission;

import com.azoth.territory.model.Government;
import com.azoth.territory.model.GovernmentForm;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A guild (guild) as a governing entity — the DTO the guilds subsystem
 * materializes from its guild database records.
 * <p>
 * Replaces the former standalone {@code RegionGuild} model: there is no
 * parallel in-memory guild world; the territory side sees only this snapshot.
 *
 * @param id stable guild identifier
 * @param name display name
 * @param government derived from the guild's chosen form and role holders
 * @param memberIds resident holder identifiers
 * @param toggles environmental and access toggles
 * @param memberPermissions effective territory permissions per member
 */
public record GuildBody(
        String id,
        String name,
        Government government,
        List<String> memberIds,
        GuildToggles toggles,
        Map<String, MemberPermissions> memberPermissions
) {
    /** Creates a guild snapshot after validating its required fields. */

    public GuildBody {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(government, "government");
        Objects.requireNonNull(memberIds, "memberIds");
        Objects.requireNonNull(toggles, "toggles");
        Objects.requireNonNull(memberPermissions, "memberPermissions");
    }
    /** Returns the guild's government form.
     *
     * @return the guild's government form
     */
    public GovernmentForm governmentForm() {
        return government.form();
    }

    /**
     * Effective territory permissions of a member, if they are a member.
     *
     * @param holderId holder identifier
     * @return the member's effective permissions, or empty if not a member
     */
    public Optional<MemberPermissions> permissionsOf(String holderId) {
        if (holderId == null || holderId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(memberPermissions.get(holderId.trim()));
    }

    /** Whether outsiders may access this guild's governed territory.
     *
     * @return whether outsiders may access this guild's governed territory
     */
    public boolean isPublic() {
        return toggles.publicEnabled();
    }
}
