package org.aincraft.guilds.territory.permission;

import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.GovernmentForm;
import org.aincraft.guilds.territory.model.Territory;

import java.util.Objects;
import java.util.Optional;

/**
 * The governing body that applies for a permission check: alliance, guild, or territory-local.
 * <p>
 * Guild and alliance bodies carry their materialized DTO snapshots so the
 * permission layer can evaluate members and toggles without touching the
 * guilds subsystem directly.
 */
public final class GoverningBody {
    public enum Kind {
        ALLIANCE,
        GUILD,
        TERRITORY,
        /** Uncontained wilderness or no body — no formal government. */
        NONE
    }

    private final Kind kind;
    private final String bodyId;
    private final Government government;
    private final GuildBody guild;
    private final AllianceBody alliance;

    private GoverningBody(Kind kind, String bodyId, Government government,
                          GuildBody guild, AllianceBody alliance) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.bodyId = bodyId;
        this.government = government == null ? Government.anarchy() : government;
        this.guild = guild;
        this.alliance = alliance;
    }

    public static GoverningBody none() {
        return new GoverningBody(Kind.NONE, null, Government.anarchy(), null, null);
    }

    public static GoverningBody ofAlliance(AllianceBody alliance) {
        Objects.requireNonNull(alliance, "alliance");
        return new GoverningBody(Kind.ALLIANCE, alliance.id(), alliance.government(), null, alliance);
    }

    public static GoverningBody ofGuild(GuildBody guild) {
        Objects.requireNonNull(guild, "guild");
        return new GoverningBody(Kind.GUILD, guild.id(), guild.government(), guild, null);
    }

    public static GoverningBody ofTerritory(Territory territory) {
        Objects.requireNonNull(territory, "territory");
        return new GoverningBody(Kind.TERRITORY, territory.id(), territory.government(), null, null);
    }

    public Kind kind() {
        return kind;
    }

    public Optional<String> bodyId() {
        return Optional.ofNullable(bodyId);
    }

    public Government government() {
        return government;
    }

    public GovernmentForm governmentForm() {
        return government.form();
    }

    public boolean hasAssignedGovernment() {
        return government.isAssigned();
    }

    public Optional<GuildBody> guildBody() {
        return Optional.ofNullable(guild);
    }

    public Optional<AllianceBody> allianceBody() {
        return Optional.ofNullable(alliance);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GoverningBody that)) {
            return false;
        }
        return kind == that.kind
                && Objects.equals(bodyId, that.bodyId)
                && government.equals(that.government)
                && Objects.equals(guild, that.guild)
                && Objects.equals(alliance, that.alliance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, bodyId, government, guild, alliance);
    }

    @Override
    public String toString() {
        return "GoverningBody{kind=" + kind + ", id=" + bodyId
                + ", form=" + government.form() + '}';
    }
}
