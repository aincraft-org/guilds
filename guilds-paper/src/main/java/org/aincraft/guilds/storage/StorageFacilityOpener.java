package org.aincraft.guilds.storage;

import org.aincraft.guilds.storage.gui.GuildStorageGUI;
import org.aincraft.guilds.territory.building.FacilityAnchorValidator;
import org.aincraft.guilds.territory.building.StorageFacilityInteractEvent;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.permission.GovernanceRegistry;
import org.aincraft.guilds.territory.permission.GuildBody;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Resolves active storage anchors and opens the guild storage GUI locally. */
public final class StorageFacilityOpener {
    public enum Outcome {
        OPENED,
        DENIED,
        UNAVAILABLE,
        NOT_APPLICABLE
    }

    public record Result(Outcome outcome, String message) {
        public static Result opened() {
            return new Result(Outcome.OPENED, "");
        }

        public static Result denied(String message) {
            return new Result(Outcome.DENIED, message);
        }

        public static Result unavailable(String message) {
            return new Result(Outcome.UNAVAILABLE, message);
        }

        public static Result notApplicable() {
            return new Result(Outcome.NOT_APPLICABLE, "");
        }
    }

    private final FacilityRegistry facilities;
    private final TerritoryRegistry territories;
    private final FacilityAnchorValidator anchors;
    private final GovernanceRegistry governance;
    private final GuildStorageGUI storageGui;
    private final PluginManager pluginManager;

    public StorageFacilityOpener(
            FacilityRegistry facilities,
            TerritoryRegistry territories,
            FacilityAnchorValidator anchors,
            GovernanceRegistry governance,
            GuildStorageGUI storageGui,
            PluginManager pluginManager) {
        this.facilities = Objects.requireNonNull(facilities, "facilities");
        this.territories = Objects.requireNonNull(territories, "territories");
        this.anchors = Objects.requireNonNull(anchors, "anchors");
        this.governance = Objects.requireNonNull(governance, "governance");
        this.storageGui = Objects.requireNonNull(storageGui, "storageGui");
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
    }

    public Result tryOpenAtLocation(Player player) {
        Objects.requireNonNull(player, "player");
        var location = player.getLocation();
        String worldId = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        Optional<SettlementFacility> facility = anchors.activeStorageAt(worldId, x, y, z)
                .or(() -> anchors.activeStorageNear(worldId, x, y, z));
        return facility
                .map(resolved -> tryOpen(player, resolved))
                .orElseGet(() -> denied("You must stand at an active guild storage facility."));
    }

    public Result tryOpen(Player player, SettlementFacility facility) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(facility, "facility");
        if (facility.type() != FacilityType.STORAGE) {
            return Result.notApplicable();
        }

        FacilityAnchorValidator.AnchorValidation validation = anchors.validate(facility);
        if (!validation.active()) {
            return denied("Storage facility anchor is inactive: " + validation.status());
        }

        Territory territory = territories.get(facility.territoryId()).orElse(null);
        if (territory == null) {
            return denied("Storage facility is outside a governed territory.");
        }

        Optional<GuildBody> governingGuild = governance.governingGuildForTerritory(facility.territoryId());
        if (governingGuild.isEmpty()) {
            return denied("Storage facility has no governing guild.");
        }
        UUID playerId = player.getUniqueId();
        if (!governingGuild.get().containsMember(playerId.toString())) {
            return denied("You are not a member of the governing guild.");
        }

        StorageFacilityInteractEvent interactEvent =
                new StorageFacilityInteractEvent(player, facility, territory, governingGuild.get().id());
        pluginManager.callEvent(interactEvent);
        if (interactEvent.isCancelled()) {
            return denied("Storage access was denied.");
        }

        storageGui.open(player, facility, governingGuild.get().id());
        return Result.opened();
    }

    private static Result denied(String message) {
        return Result.denied(message);
    }
}
