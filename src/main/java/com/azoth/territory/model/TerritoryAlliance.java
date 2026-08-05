package com.azoth.territory.model;

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
public final class TerritoryAlliance {
    private final String id;
    private final String name;
    private final Government government;
    private final List<String> territoryIds;

    private TerritoryAlliance(
            String id,
            String name,
            Government government,
            Collection<String> territoryIds
    ) {
        this.id = requireId(id);
        this.name = name == null || name.isBlank() ? this.id : name.trim();
        this.government = requireAssignedGovernment(government);
        this.territoryIds = List.copyOf(normalizeIds(territoryIds));
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
        return new TerritoryAlliance(id, name, government, territoryIds);
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

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Government government() {
        return government;
    }

    public GovernmentForm governmentForm() {
        return government.form();
    }

    public List<String> territoryIds() {
        return territoryIds;
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TerritoryAlliance that)) {
            return false;
        }
        return id.equals(that.id)
                && name.equals(that.name)
                && government.equals(that.government)
                && territoryIds.equals(that.territoryIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, government, territoryIds);
    }

    @Override
    public String toString() {
        return "TerritoryAlliance{id='" + id + "', government=" + government.form()
                + ", territories=" + territoryIds.size() + '}';
    }
}
