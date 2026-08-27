package dev.mintychochip.territory.building;

import dev.mintychochip.territory.model.FacilityType;

import java.util.Objects;

public record BuildingPlacement(FacilityType type, String id, String name, long expiresAtMillis) {
    public BuildingPlacement {
        Objects.requireNonNull(type, "type");
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("invalid building id");
        }
        name = name == null || name.isBlank() ? id : name.trim();
    }
}
