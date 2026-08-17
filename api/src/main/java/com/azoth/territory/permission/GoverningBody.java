package com.azoth.territory.permission;

import com.azoth.territory.model.Government;
import com.azoth.territory.model.GovernmentForm;
import com.azoth.territory.model.Territory;

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
    /** The kind of governing body represented by a snapshot. */
    public enum Kind {
        /** An alliance governs the body. */
        ALLIANCE,
        /** A guild governs the body. */
        GUILD,
        /** A territory-local government governs the body. */
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

    /** Returns an ungoverned body using anarchy.
     *
     * @return an ungoverned body
     */
    public static GoverningBody none() {
        return new GoverningBody(Kind.NONE, null, Government.anarchy(), null, null);
    }

    /**
     * Creates an alliance governing body.
     *
     * @param alliance alliance snapshot
     * @return governing body backed by the alliance
     */
    public static GoverningBody ofAlliance(AllianceBody alliance) {
        Objects.requireNonNull(alliance, "alliance");
        return new GoverningBody(Kind.ALLIANCE, alliance.id(), alliance.government(), null, alliance);
    }
    /**
     * Creates a guild governing body.
     *
     * @param guild guild snapshot
     * @return governing body backed by the guild
     */
    public static GoverningBody ofGuild(GuildBody guild) {
        Objects.requireNonNull(guild, "guild");
        return new GoverningBody(Kind.GUILD, guild.id(), guild.government(), guild, null);
    }

    /**
     * Creates a territory governing body.
     *
     * @param territory territory snapshot
     * @return governing body backed by the territory
     */
    public static GoverningBody ofTerritory(Territory territory) {
        Objects.requireNonNull(territory, "territory");
        return new GoverningBody(Kind.TERRITORY, territory.id(), territory.government(), null, null);
    }

    /** Returns the governing body kind.
     *
     * @return the governing body kind
     */
    public Kind kind() {
        return kind;
    }

    /** Returns the optional governing body identifier.
     *
     * @return the governing body identifier, if present
     */
    public Optional<String> bodyId() {
        return Optional.ofNullable(bodyId);
    }

    /** Returns the governing body's government.
     *
     * @return the governing body's government
     */
    public Government government() {
        return government;
    }

    /** Returns the governing body's government form.
     *
     * @return the governing body's government form
     */
    public GovernmentForm governmentForm() {
        return government.form();
    }

    /** Returns whether the government has assigned authority.
     *
     * @return whether the government has assigned authority
     */
    public boolean hasAssignedGovernment() {
        return government.isAssigned();
    }

    /** Returns the optional guild snapshot.
     *
     * @return the guild snapshot, if present
     */
    public Optional<GuildBody> guildBody() {
        return Optional.ofNullable(guild);
    }

    /** Returns the optional alliance snapshot.
     *
     * @return the alliance snapshot, if present
     */
    public Optional<AllianceBody> allianceBody() {
        return Optional.ofNullable(alliance);
    }

    /**
     * Compares this body with another object.
     *
     * @param o object to compare
     * @return whether the objects represent the same body
     */
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

    /** @return a hash code for this governing body. */
    @Override
    public int hashCode() {
        return Objects.hash(kind, bodyId, government, guild, alliance);
    }

    /** @return a concise textual representation of this body. */
    @Override
    public String toString() {
        return "GoverningBody{kind=" + kind + ", id=" + bodyId
                + ", form=" + government.form() + '}';
    }
}
