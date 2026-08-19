package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;

/** Validates only the registered block coordinate; neighboring construction is irrelevant. */
public final class FacilityAnchorValidator {
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

    public AnchorValidation validate(SettlementFacility facility) {
        Objects.requireNonNull(facility, "facility");
        boolean inside = territories.resolve(facility.worldId(), facility.x(), facility.z())
                .territoryId().filter(facility.territoryId()::equals).isPresent();
        if (!inside) {
            return new AnchorValidation(AnchorStatus.OUTSIDE_TERRITORY, facility);
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

    public record AnchorValidation(AnchorStatus status, SettlementFacility facility) {
        public boolean active() {
            return status == AnchorStatus.ACTIVE;
        }
    }
}
