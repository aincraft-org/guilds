package org.aincraft.guilds.storage.service;

import org.aincraft.guilds.territory.building.FacilityAnchorValidator;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.permission.GovernanceRegistry;
import org.aincraft.guilds.territory.permission.GuildBody;
import org.aincraft.guilds.territory.registry.FacilityRegistry;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Validates storage access using trusted territory and facility registries. */
public final class RegistryStorageFacilityAccessValidator implements StorageFacilityAccessValidator {
    private final PlayerLocationSource locations;
    private final FacilityRegistry facilities;
    private final FacilityAnchorValidator anchors;
    private final GovernanceRegistry governance;

    public RegistryStorageFacilityAccessValidator(
            PlayerLocationSource locations,
            FacilityRegistry facilities,
            FacilityAnchorValidator anchors,
            GovernanceRegistry governance) {
        this.locations = Objects.requireNonNull(locations, "locations");
        this.facilities = Objects.requireNonNull(facilities, "facilities");
        this.anchors = Objects.requireNonNull(anchors, "anchors");
        this.governance = Objects.requireNonNull(governance, "governance");
    }

    @Override
    public StorageResult<Void> validateMutationAccess(UUID actor, String guildId, String facilityId) {
        if (actor == null) {
            return StorageResult.failure(StorageResult.Status.UNAUTHORIZED, "Actor is required");
        }
        if (guildId == null || guildId.isBlank()) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "guildId is required");
        }
        if (facilityId == null || facilityId.isBlank()) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "facilityId is required");
        }

        Optional<SettlementFacility> facility = facilities.get(facilityId.trim());
        if (facility.isEmpty()) {
            return StorageResult.failure(StorageResult.Status.PERMISSION_DENIED, "Unknown storage facility");
        }
        SettlementFacility resolvedFacility = facility.get();
        if (resolvedFacility.type() != FacilityType.STORAGE) {
            return StorageResult.failure(StorageResult.Status.PERMISSION_DENIED, "Facility is not storage");
        }

        Optional<PlayerLocationSource.BlockLocation> actorLocation = locations.locationOf(actor);
        if (actorLocation.isEmpty()) {
            return StorageResult.failure(StorageResult.Status.PERMISSION_DENIED, "Actor location unavailable");
        }
        PlayerLocationSource.BlockLocation location = actorLocation.get();
        Optional<SettlementFacility> atLocation = anchors.activeStorageAt(
                        location.worldId(), location.x(), location.y(), location.z())
                .or(() -> anchors.activeStorageNear(
                        location.worldId(), location.x(), location.y(), location.z()));
        if (atLocation.isEmpty() || !atLocation.get().id().equals(resolvedFacility.id())) {
            return StorageResult.failure(StorageResult.Status.PERMISSION_DENIED, "Actor is not at storage facility");
        }

        FacilityAnchorValidator.AnchorValidation anchorValidation = anchors.validate(resolvedFacility);
        if (!anchorValidation.active()) {
            return StorageResult.failure(
                    StorageResult.Status.PERMISSION_DENIED,
                    "Storage facility anchor is inactive: " + anchorValidation.status());
        }

        Optional<GuildBody> governingGuild = governance.governingGuildForTerritory(resolvedFacility.territoryId());
        if (governingGuild.isEmpty() || !governingGuild.get().id().equals(guildId.trim())) {
            return StorageResult.failure(
                    StorageResult.Status.PERMISSION_DENIED, "Storage facility is not governed by guild");
        }
        if (!governingGuild.get().containsMember(actor.toString())) {
            return StorageResult.failure(
                    StorageResult.Status.UNAUTHORIZED, "Actor is not a resident of governing guild");
        }
        return StorageResult.success(null);
    }
}
