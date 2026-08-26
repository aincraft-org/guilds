package dev.mintychochip.territory.model;

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

    /** Creates an uncontained lookup result.
     * @return an uncontained lookup result
     */
    public static LookupResult uncontained() {
        return new LookupResult(null, null);
    }

    /** Creates a contained lookup result.
     * @param territory resolved territory
     * @param zone resolved zone
     * @return a contained lookup result
     * @throws NullPointerException if either argument is {@code null}
     */
    public static LookupResult of(Territory territory, Territory.ZoneResolution zone) {
        Objects.requireNonNull(territory, "territory");
        Objects.requireNonNull(zone, "zone");
        return new LookupResult(territory, zone);
    }

    /** Reports whether a territory was resolved.
     * @return whether a territory was resolved
     */
    public boolean isContained() {
        return territory != null;
    }

    /** Returns the resolved territory, if any.
     * @return the resolved territory, if any
     */
    public Optional<Territory> territory() {
        return Optional.ofNullable(territory);
    }

    /** Returns the resolved territory identifier, if any.
     * @return the resolved territory identifier, if any
     */
    public Optional<String> territoryId() {
        return territory == null ? Optional.empty() : Optional.of(territory.id());
    }

    /** Returns the resolved zone, if any.
     * @return the resolved zone, if any
     */
    public Optional<Territory.ZoneResolution> zone() {
        return Optional.ofNullable(zone);
    }

    /** Returns the resolved zone type, if any.
     * @return the resolved zone type, if any
     */
    public Optional<ZoneType> zoneType() {
        return zone == null ? Optional.empty() : Optional.of(zone.type());
    }

    /**
     * Government of the resolved territory, if any (form may still be {@link GovernmentForm#ANARCHY}).
     * @return the resolved government, if any
     */
    public Optional<Government> government() {
        return territory == null ? Optional.empty() : Optional.of(territory.government());
    }

    /** Returns the resolved government form, if any.
     * @return the resolved government form, if any
     */
    public Optional<GovernmentForm> governmentForm() {
        return government().map(Government::form);
    }

    /** @param o object to compare
     * @return whether both lookup results are equal
     */
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

    /** @return hash code for this lookup result */
    @Override
    public int hashCode() {
        return Objects.hash(territory, zone);
    }

    /** @return concise textual representation */
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
