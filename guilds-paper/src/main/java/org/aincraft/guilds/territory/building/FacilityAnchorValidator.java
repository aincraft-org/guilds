package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;

/** Validates only the registered block coordinate; neighboring construction is irrelevant. */
public final class FacilityAnchorValidator {
    /** Chebyshev distance for physical storage access and supporting-anchor resolution. */
    public static final int PHYSICAL_ACCESS_RADIUS = 1;

    private final Server server;
    private final TerritoryRegistry territories;
    private final FacilityRegistry facilities;
    private final BuildingConfig config;

    public FacilityAnchorValidator(Server server, TerritoryRegistry territories,
                                   FacilityRegistry facilities, BuildingConfig config) {
        this.server = Objects.requireNonNull(server, "server");
        this.territories = Objects.requireNonNull(territories, "territories");
        this.facilities = Objects.requireNonNull(facilities, "facilities");
        this.config = Objects.requireNonNull(config, "config");
    }
    Server server() {
        return server;
    }


    public AnchorValidation validate(SettlementFacility facility) {
        Objects.requireNonNull(facility, "facility");
        boolean inside = territories.resolve(facility.worldId(), facility.x(), facility.z())
                .territoryId().filter(facility.territoryId()::equals).isPresent();
        if (!inside) {
            return new AnchorValidation(AnchorStatus.OUTSIDE_TERRITORY, facility);
        }
        if (isTransport(facility.type())) {
            BuildingConfig.TransportGeometry geometry = config.transportGeometry();
            if (geometry == null) {
                return new AnchorValidation(AnchorStatus.MISSING_GEOMETRY, facility);
            }
            if (geometry.boatEntryRadius() <= 0 || geometry.boatEntryWidth() <= 0
                    || geometry.clearBoatSpaceHeight() <= 0 || geometry.searchChunkRadius() <= 0
                    || geometry.searchChunkBudget() <= 0 || geometry.airshipPlatformRadius() <= 0
                    || geometry.airshipVerticalClearanceHeight() <= 0) {
                return new AnchorValidation(AnchorStatus.INVALID_GEOMETRY, facility);
            }
        }
        World world = server.getWorld(facility.worldId());
        if (world == null || !world.isChunkLoaded(facility.x() >> 4, facility.z() >> 4)) {
            return new AnchorValidation(AnchorStatus.WORLD_UNAVAILABLE, facility);
        }
        boolean allowed = config.anchorMaterials(facility.type())
                .contains(world.getBlockAt(facility.x(), facility.y(), facility.z()).getType());
        return new AnchorValidation(allowed ? AnchorStatus.ACTIVE : AnchorStatus.WRONG_MATERIAL, facility);
    }

    public Optional<SettlementFacility> activeAt(String worldId, int x, int y, int z) {
        return facilities.resolve(worldId, x, y, z).filter(facility -> validate(facility).active());
    }

    public Optional<SettlementFacility> activeStorageAt(String worldId, int x, int y, int z) {
        return facilities.resolve(worldId, x, y, z)
                .filter(facility -> facility.type() == FacilityType.STORAGE)
                .filter(facility -> validate(facility).active());
    }

    public Optional<SettlementFacility> activeNear(String worldId, int x, int y, int z, int radius) {
        return facilities.resolveNearby(worldId, x, y, z, radius)
                .filter(facility -> validate(facility).active());
    }

    public Optional<SettlementFacility> activeStorageNear(String worldId, int x, int y, int z) {
        if (worldId == null) {
            return Optional.empty();
        }
        String normalizedWorld = worldId.trim();
        SettlementFacility nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (SettlementFacility facility : facilities.list()) {
            if (facility.type() != FacilityType.STORAGE || !facility.worldId().equals(normalizedWorld)) {
                continue;
            }
            if (!validate(facility).active()) {
                continue;
            }
            long dx = (long) facility.x() - x;
            long dy = (long) facility.y() - y;
            long dz = (long) facility.z() - z;
            if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) > PHYSICAL_ACCESS_RADIUS) {
                continue;
            }
            long distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance
                    || (distance == nearestDistance
                            && (nearest == null || facility.id().compareTo(nearest.id()) < 0))) {
                nearest = facility;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }
    private static boolean isTransport(FacilityType type) {
        return type == FacilityType.BOAT || type == FacilityType.AIRSHIP;
    }


    public record AnchorValidation(AnchorStatus status, SettlementFacility facility) {
        public boolean active() {
            return status == AnchorStatus.ACTIVE;
        }

        public boolean isActive() {
            return active();
        }

        public String category() {
            return status.name();
        }

        public String failureCategory() {
            return category();
        }
    }
}
