package com.azoth.territory.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of resolving a world location against the territory registry.
 */
public final class LookupResult {
    private final Territory territory;
    private final Territory.ZoneResolution zone;

    private LookupResult(Territory territory, Territory.ZoneResolution zone) {
        this.territory = territory;
        this.zone = zone;
    }

    public static LookupResult uncontained() {
        return new LookupResult(null, null);
    }

    public static LookupResult of(Territory territory, Territory.ZoneResolution zone) {
        Objects.requireNonNull(territory, "territory");
        Objects.requireNonNull(zone, "zone");
        return new LookupResult(territory, zone);
    }

    public boolean isContained() {
        return territory != null;
    }

    public Optional<Territory> territory() {
        return Optional.ofNullable(territory);
    }

    public Optional<String> territoryId() {
        return territory == null ? Optional.empty() : Optional.of(territory.id());
    }

    public Optional<Territory.ZoneResolution> zone() {
        return Optional.ofNullable(zone);
    }

    public Optional<ZoneType> zoneType() {
        return zone == null ? Optional.empty() : Optional.of(zone.type());
    }

    /**
     * Government of the resolved territory, if any (form may still be {@link GovernmentForm#ANARCHY}).
     */
    public Optional<Government> government() {
        return territory == null ? Optional.empty() : Optional.of(territory.government());
    }

    public Optional<GovernmentForm> governmentForm() {
        return government().map(Government::form);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LookupResult that)) {
            return false;
        }
        return Objects.equals(territory, that.territory) && Objects.equals(zone, that.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(territory, zone);
    }

    @Override
    public String toString() {
        if (!isContained()) {
            return "LookupResult{uncontained}";
        }
        return "LookupResult{territory=" + territory.id()
                + ", zoneType=" + zone.type()
                + ", zoneId=" + zone.zoneId()
                + ", default=" + zone.isDefault()
                + ", government=" + territory.governmentForm() + '}';
    }
}
