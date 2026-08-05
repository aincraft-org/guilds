package com.azoth.territory.permission;

import com.azoth.territory.model.Government;
import com.azoth.territory.model.GovernmentForm;
import com.azoth.territory.model.RegionGuild;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.TerritoryAlliance;

import java.util.Objects;
import java.util.Optional;

/**
 * The governing body that applies for a permission check: alliance, guild, or territory-local.
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

    private GoverningBody(Kind kind, String bodyId, Government government) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.bodyId = bodyId;
        this.government = government == null ? Government.anarchy() : government;
    }

    public static GoverningBody none() {
        return new GoverningBody(Kind.NONE, null, Government.anarchy());
    }

    public static GoverningBody ofAlliance(TerritoryAlliance alliance) {
        Objects.requireNonNull(alliance, "alliance");
        return new GoverningBody(Kind.ALLIANCE, alliance.id(), alliance.government());
    }

    public static GoverningBody ofGuild(RegionGuild guild) {
        Objects.requireNonNull(guild, "guild");
        return new GoverningBody(Kind.GUILD, guild.id(), guild.government());
    }

    public static GoverningBody ofTerritory(Territory territory) {
        Objects.requireNonNull(territory, "territory");
        return new GoverningBody(Kind.TERRITORY, territory.id(), territory.government());
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
                && government.equals(that.government);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, bodyId, government);
    }

    @Override
    public String toString() {
        return "GoverningBody{kind=" + kind + ", id=" + bodyId
                + ", form=" + government.form() + '}';
    }
}
