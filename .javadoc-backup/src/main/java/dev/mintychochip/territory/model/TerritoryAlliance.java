package dev.mintychochip.territory.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Alliance of territories formed with an assigned government.
 *
 * @param id alliance identifier
 * @param name display name
 * @param government assigned government
 * @param territoryIds member territory identifiers
 */
public record TerritoryAlliance(String id, String name, Government government, List<String> territoryIds) {
    /**
     * Validates and normalizes an alliance.
     *
     * @throws IllegalArgumentException if an identifier or government is invalid
     */
    public TerritoryAlliance {
        id = requireId(id);
        name = name == null || name.isBlank() ? id : name.trim();
        government = requireAssignedGovernment(government);
        territoryIds = List.copyOf(normalizeIds(territoryIds));
    }

    /**
     * Form an alliance with no member territories.
     *
     * @param id alliance identifier
     * @param name display name
     * @param government assigned government
     * @return formed alliance
     */
    public static TerritoryAlliance form(String id, String name, Government government) {
        return form(id, name, government, List.of());
    }

    /**
     * Form an alliance with initial member territories.
     *
     * @param id alliance identifier
     * @param name display name
     * @param government assigned government
     * @param territoryIds initial territory identifiers
     * @return formed alliance
     */
    public static TerritoryAlliance form(
            String id,
            String name,
            Government government,
            Collection<String> territoryIds
    ) {
        return new TerritoryAlliance(id, name, government, territoryIds == null ? List.of() : new ArrayList<>(territoryIds));
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        return id.trim();
    }

    private static Government requireAssignedGovernment(Government government) {
        if (government == null || !government.isAssigned()) {
            throw new IllegalArgumentException(
                    "territory alliance must pick an assigned government at formation (not ANARCHY)"
            );
        }
        return government;
    }

    private static List<String> normalizeIds(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String h : raw) {
            if (h != null && !h.isBlank()) {
                seen.add(h.trim());
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * Returns the assigned government form.
     *
     * @return government form
     */
    public GovernmentForm governmentForm() {
        return government.form();
    }

    /**
     * Tests whether a territory is present.
     *
     * @param territoryId territory identifier
     * @return {@code true} when the territory is present
     */
    public boolean containsTerritory(String territoryId) {
        if (territoryId == null || territoryId.isBlank()) {
            return false;
        }
        return territoryIds.contains(territoryId.trim());
    }

    /**
     * Returns a copy using another government.
     *
     * @param next assigned replacement government
     * @return updated alliance
     */
    public TerritoryAlliance withGovernment(Government next) {
        return new TerritoryAlliance(id, name, next, territoryIds);
    }

    /**
     * Returns a copy containing the territory.
     *
     * @param territoryId territory identifier
     * @return updated alliance, or this instance when unchanged
     */
    public TerritoryAlliance withTerritory(String territoryId) {
        if (territoryId == null || territoryId.isBlank()) {
            return this;
        }
        String trimmed = territoryId.trim();
        if (territoryIds.contains(trimmed)) {
            return this;
        }
        List<String> next = new ArrayList<>(territoryIds);
        next.add(trimmed);
        return new TerritoryAlliance(id, name, government, next);
    }

    /**
     * Returns a copy without the territory.
     *
     * @param territoryId territory identifier
     * @return updated alliance, or this instance when unchanged
     */
    public TerritoryAlliance withoutTerritory(String territoryId) {
        if (territoryId == null || territoryId.isBlank()) {
            return this;
        }
        String trimmed = territoryId.trim();
        if (!territoryIds.contains(trimmed)) {
            return this;
        }
        List<String> next = new ArrayList<>(territoryIds);
        next.remove(trimmed);
        return new TerritoryAlliance(id, name, government, next);
    }

    @Override
    public String toString() {
        return "TerritoryAlliance{id='" + id + "', government=" + government.form()
                + ", territories=" + territoryIds.size() + '}';
    }
}
