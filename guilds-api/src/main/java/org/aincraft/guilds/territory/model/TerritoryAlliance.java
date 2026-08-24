package org.aincraft.guilds.territory.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Alliance of territories: formed with an assigned {@link Government}.
 * <p>
 * Governments live at the territory-alliance layer for multi-territory sovereignty.
 * Member ids are territory ids (opaque strings matching {@link Territory#id()}).
 */
public record TerritoryAlliance(String id, String name, Government government, List<String> territoryIds) {

    public TerritoryAlliance {
        id = requireId(id);
        name = name == null || name.isBlank() ? id : name.trim();
        government = requireAssignedGovernment(government);
        territoryIds = List.copyOf(normalizeIds(territoryIds));
    }

    /**
     * Form a territory alliance with a chosen government and no member territories yet.
     */
    public static TerritoryAlliance form(String id, String name, Government government) {
        return form(id, name, government, List.of());
    }

    /**
     * Form a territory alliance with a chosen government and initial member territories.
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

    public GovernmentForm governmentForm() {
        return government.form();
    }

    public boolean containsTerritory(String territoryId) {
        if (territoryId == null || territoryId.isBlank()) {
            return false;
        }
        return territoryIds.contains(territoryId.trim());
    }

    public TerritoryAlliance withGovernment(Government next) {
        return new TerritoryAlliance(id, name, next, territoryIds);
    }

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
