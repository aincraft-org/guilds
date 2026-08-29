package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.inventory.EquipmentSlot;
import org.aincraft.guilds.storage.StorageFacilityOpener;
import org.aincraft.guilds.territory.model.FacilityType;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Registers and protects exact facility anchors; neighboring blocks are ignored. */
public final class BuildingListener implements Listener {
    private static final Logger LOGGER = Logger.getLogger(BuildingListener.class.getName());

    private final BuildingPlacementSessions sessions;
    private final BuildingConfig config;
    private final TerritoryRegistry territories;
    private final FacilityRegistry facilities;
    private final BuildingAuthorization authorization;
    private final FacilityMutationService mutations;
    private final FacilityAnchorValidator anchors;
    private final FastTravelAccess fastTravel;
    private final FastTravelSelections selections;
    private final FastTravelService travel;
    private final PluginManager pluginManager;
    private final StorageFacilityOpener storageOpener;

    public BuildingListener(BuildingPlacementSessions sessions, BuildingConfig config,
                            TerritoryRegistry territories, FacilityRegistry facilities,
                            BuildingAuthorization authorization, FacilityMutationService mutations,
                            FacilityAnchorValidator anchors, FastTravelAccess fastTravel,
                            FastTravelSelections selections, PluginManager pluginManager,
                            StorageFacilityOpener storageOpener) {
        this(sessions, config, territories, facilities, authorization, mutations, anchors,
                fastTravel, selections, pluginManager, storageOpener, null);
    }

    public BuildingListener(BuildingPlacementSessions sessions, BuildingConfig config,
                            TerritoryRegistry territories, FacilityRegistry facilities,
                            BuildingAuthorization authorization, FacilityMutationService mutations,
                            FacilityAnchorValidator anchors, FastTravelAccess fastTravel,
                            FastTravelSelections selections, PluginManager pluginManager,
                            StorageFacilityOpener storageOpener, FastTravelService travel) {
        this.sessions = sessions;
        this.config = config;
        this.territories = territories;
        this.facilities = facilities;
        this.authorization = authorization;
        this.mutations = mutations;
        this.anchors = anchors;
        this.fastTravel = fastTravel;
        this.selections = selections;
        this.pluginManager = pluginManager;
        this.storageOpener = storageOpener;
        this.travel = travel;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        BuildingPlacement placement = sessions.current(player.getUniqueId(), System.currentTimeMillis())
                .orElse(null);
        if (placement == null) {
            interactWithActiveAnchor(event, player, event.getClickedBlock());
            return;
        }
        Block block = event.getClickedBlock();
        if (!config.anchorMaterials(placement.type()).contains(block.getType())) {
            player.sendMessage(Component.text("That block cannot anchor a " + placement.type() + ".",
                    NamedTextColor.RED));
            return;
        }
        Territory territory = territories.resolve(block.getWorld().getName(), block.getX(), block.getZ())
                .territory().orElse(null);
        if (territory == null) {
            player.sendMessage(Component.text("The anchor must be inside a territory.", NamedTextColor.RED));
            return;
        }
        if (!authorization.canManage(player, territory)) {
            sessions.complete(player.getUniqueId());
            player.sendMessage(Component.text("You cannot manage buildings in this territory.",
                    NamedTextColor.RED));
            return;
        }
        SettlementFacility facility = new SettlementFacility(
                placement.id(), placement.name(), territory.id(), placement.type(),
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        try {
            mutations.register(facility);
            sessions.complete(player.getUniqueId());
            event.setCancelled(true);
            player.sendMessage(Component.text("Registered " + facility.name() + ".", NamedTextColor.GREEN));
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Building registration failed for " + player.getName(), e);
            String category = e.getMessage() == null || e.getMessage().isBlank()
                    ? "unknown failure" : e.getMessage();
            player.sendMessage(Component.text("Building registration failed: " + category,
                    NamedTextColor.RED));
        }
    }

    private void interactWithActiveAnchor(PlayerInteractEvent event, Player player, Block block) {
        SettlementFacility facility = anchors.activeAt(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ()).orElse(null);
        if (facility != null) {
            if (facility.type() == FacilityType.STORAGE) {
                interactWithStorageAnchor(event, player, facility);
                return;
            }
            if (facility.type() == FacilityType.TRADING_POST) {
                Territory territory = territories.get(facility.territoryId()).orElse(null);
                if (territory == null) {
                    return;
                }
                TradingPostInteractEvent tradingPostEvent =
                        new TradingPostInteractEvent(player, facility, territory);
                pluginManager.callEvent(tradingPostEvent);
                event.setCancelled(true);
                player.sendMessage(Component.text(tradingPostEvent.isCancelled()
                                ? "Trading post interaction is unavailable."
                                : facility.name() + " — " + territory.name(),
                        tradingPostEvent.isCancelled() ? NamedTextColor.RED : NamedTextColor.GOLD));
                return;
            }
            if (facility.type() == FacilityType.WAYSTONE
                    || facility.type() == FacilityType.GUILD_CRYSTAL
                    || facility.type() == FacilityType.TELEPORT_TERMINAL
                    || facility.type() == FacilityType.BOAT
                    || facility.type() == FacilityType.AIRSHIP) {
                var reachable = facility.type() == FacilityType.WAYSTONE
                        ? fastTravel.reachable(player.getUniqueId(), facility)
                        : fastTravel.destinations(player.getUniqueId(), facility);
                event.setCancelled(true);
                if (facility.type() == FacilityType.TELEPORT_TERMINAL && travel != null) {
                    SettlementFacility ownCrystal = reachable.stream()
                            .filter(candidate -> {
                                FastTravelAccess.AccessDecision decision =
                                        fastTravel.authorize(player.getUniqueId(), facility, candidate);
                                return decision != null && decision.allowed()
                                        && java.util.Objects.equals(decision.travelerGuildId(),
                                        decision.destinationGuildId());
                            })
                            .findFirst().orElse(null);
                    if (ownCrystal != null) {
                        travel.start(player, facility, ownCrystal.id(), System.currentTimeMillis())
                                .whenComplete((result, error) -> travel.executeOnMain(
                                        () -> player.sendMessage(Component.text(
                                                error == null
                                                        && result == FastTravelService.StartResult.STARTED
                                                        ? "Fast travel warming up." : "Fast travel failed.",
                                                error == null
                                                        && result == FastTravelService.StartResult.STARTED
                                                        ? NamedTextColor.GREEN : NamedTextColor.RED))));
                        reachable = reachable.stream()
                                .filter(candidate -> !candidate.id().equals(ownCrystal.id()))
                                .toList();
                    }
                }
                if (facility.type() == FacilityType.TELEPORT_TERMINAL && reachable.isEmpty()) {
                    return;
                }
                selections.select(player.getUniqueId(), facility.id(), System.currentTimeMillis());
                String label = facility.type() == FacilityType.WAYSTONE
                        ? "Reachable waystones:" : "Eligible fast-travel destinations:";
                player.sendMessage(Component.text(label, NamedTextColor.GOLD));
                for (SettlementFacility destination : reachable) {
                    player.sendMessage(Component.text(" • " + destination.name(), NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand(
                                    "/territory building travel " + destination.id())));
                }
                return;
            }
        }

        Optional<SettlementFacility> nearbyStorage = anchors.activeStorageNear(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (nearbyStorage.isPresent()) {
            interactWithStorageAnchor(event, player, nearbyStorage.get());
        }
    }

    private void interactWithStorageAnchor(PlayerInteractEvent event, Player player, SettlementFacility facility) {
        if (storageOpener == null) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Guild storage is unavailable.", NamedTextColor.RED));
            return;
        }
        StorageFacilityOpener.Result result = storageOpener.tryOpen(player, facility);
        if (result.outcome() == StorageFacilityOpener.Outcome.NOT_APPLICABLE) {
            return;
        }
        event.setCancelled(true);
        if (result.outcome() != StorageFacilityOpener.Outcome.OPENED) {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        SettlementFacility facility = facilities.resolve(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ()).orElse(null);
        if (facility == null) {
            return;
        }
        Territory territory = territories.get(facility.territoryId()).orElse(null);
        if (territory == null || !authorization.canManage(event.getPlayer(), territory)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("You cannot break this facility anchor.",
                    NamedTextColor.RED));
            return;
        }
        event.setCancelled(true);
        try {
            mutations.remove(facility.id());
            event.setCancelled(false);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Facility removal failed for " + event.getPlayer().getName(), e);
            String category = e.getMessage() == null || e.getMessage().isBlank()
                    ? "unknown failure" : e.getMessage();
            event.getPlayer().sendMessage(Component.text(
                    "Could not remove the facility: " + category, NamedTextColor.RED));
        }
    }
}
