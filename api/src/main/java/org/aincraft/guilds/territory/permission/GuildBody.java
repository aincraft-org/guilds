package org.aincraft.guilds.territory.permission;

import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.GovernmentForm;

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
 * @param government derived from the guild's chosen form + role holders
 * @param memberIds resident holder ids (player UUID strings)
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

    public GuildBody {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(government, "government");
        Objects.requireNonNull(memberIds, "memberIds");
        Objects.requireNonNull(toggles, "toggles");
        Objects.requireNonNull(memberPermissions, "memberPermissions");
    }

    public GovernmentForm governmentForm() {
        return government.form();
    }

    public boolean containsMember(String holderId) {
        return holderId != null && !holderId.isBlank() && memberIds.contains(holderId.trim());
    }

    /**
     * Effective territory permissions of a member, if they are a member.
     */
    public Optional<MemberPermissions> permissionsOf(String holderId) {
        if (holderId == null || holderId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(memberPermissions.get(holderId.trim()));
    }

    public boolean isPublic() {
        return toggles.publicEnabled();
    }
}
