package dev.mintychochip.territory.listener;

import dev.mintychochip.territory.permission.BlockProtection;
import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Paper edge for player/entity interaction vectors beyond break/place:
 * container open / hopper steal, doors/buttons/levers/beds (interact), item
 * frames / armor stands / paintings, vehicle placement and grief, animal
 * kill / leash, PvP and friendly-fire by territory, and forced teleports /
 * spawn setting inside governed land.
 * <p>
 * Actor-driven actions are gated by {@link BlockProtection#canInteract} /
 * canInteractWithEntity (same formal-authority rule as break/place).
 * Actorless mechanical vectors (hoppers pulling items out of a claim) have no
 * authority holder, so they are denied whenever the source inventory sits in
 * assigned-government land — the conservative deny documented in the design.
 * No per-group flags, company identities, or per-player home registries exist
 * in the data model yet; those are future work.
 */
public final class InteractionProtectionListener implements Listener {

    /** Teleport causes that are player-forced (not physical movement). */
    private static final Set<PlayerTeleportEvent.TeleportCause> FORCED_TELEPORT_CAUSES =
            EnumSet.of(
                    PlayerTeleportEvent.TeleportCause.COMMAND,
                    PlayerTeleportEvent.TeleportCause.PLUGIN,
                    PlayerTeleportEvent.TeleportCause.NETHER_PORTAL,
                    PlayerTeleportEvent.TeleportCause.END_PORTAL,
                    PlayerTeleportEvent.TeleportCause.END_GATEWAY,
                    PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT,
                    PlayerTeleportEvent.TeleportCause.SPECTATE,
                    PlayerTeleportEvent.TeleportCause.ENDER_PEARL
            );

    private final BlockProtection protection;

    public InteractionProtectionListener(BlockProtection protection) {
        this.protection = Objects.requireNonNull(protection, "protection");
    }

    public BlockProtection protection() {
        return protection;
    }

    /**
     * Container open (chests, shulker boxes, barrels, furnaces, hoppers, horses).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Location loc = inventoryLocation(event.getInventory());
        if (loc == null) {
            return;
        }
        if (!protection.canInteract(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Hopper/dropper steal: items pulled out of a governed container by a
     * mechanical actor. No attribution exists — the source location decides.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (denyMechanicalMove(event.getSource())
                || denyMechanicalMove(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    /**
     * Hopper-style pickup of dropped items in governed land.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        if (denyMechanicalMove(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    /**
     * Mechanical (non-player) item movement involving governed land is denied:
     * engine-detected hoppers have no authority holder, so any transfer to or
     * from an assigned-government inventory is blocked.
     */
    private boolean denyMechanicalMove(Inventory inventory) {
        Location loc = inventoryLocation(inventory);
        if (loc == null) {
            return false;
        }
        String world = loc.getWorld().getName();
        return protection.isEnvironmentallyProtected(world, loc.getBlockX(), loc.getBlockZ());
    }

    /**
     * Item frames, armor stands, paintings: placement.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!protection.canInteract(
                block.getWorld().getName(), block.getX(), block.getZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Item frames, armor stands, paintings: break (player or projectile).
     * Non-player breakers (mobs, explosions) are always denied in governed land.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        Location loc = event.getEntity().getLocation();
        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        if (remover instanceof Player player) {
            if (!protection.canInteract(
                    world, x, z, player.getUniqueId().toString())) {
                event.setCancelled(true);
            }
            return;
        }
        if (protection.isEnvironmentallyProtected(world, x, z)) {
            event.setCancelled(true);
        }
    }

    /**
     * Armor stand / item frame / any entity interaction (rotate, equip, swap).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getRightClicked().getLocation();
        if (!protection.canInteractWithEntity(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Armor stand-specific manipulation (shows up alongside interact events).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getRightClicked().getLocation();
        if (!protection.canInteractWithEntity(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Item frame item insertion/rotation/removal (Paper event).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemFrameChange(PlayerItemFrameChangeEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getItemFrame().getLocation();
        if (!protection.canInteractWithEntity(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Armor stand / item frame placement in creative (EntityPlaceEvent).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Block block = event.getBlock();
        if (!protection.canInteract(
                block.getWorld().getName(), block.getX(), block.getZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Boat placement (bucket of boat / axolotl): gate on the clicked block,
     * same as place.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockClicked();
        if (block == null) {
            return;
        }
        if (!protection.canPlace(
                block.getWorld().getName(), block.getX(), block.getZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Entering a vehicle that sits in a claim = interacting with the claim.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) {
            return;
        }
        Location loc = event.getVehicle().getLocation();
        if (!protection.canInteract(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Destroying a vehicle in a claim (break boat/minecart).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getAttacker() instanceof Player player)) {
            return;
        }
        Location loc = event.getVehicle().getLocation();
        if (!protection.canInteract(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Vehicle destroy path (kills the vehicle).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getAttacker() instanceof Player player)) {
            return;
        }
        Location loc = event.getVehicle().getLocation();
        if (!protection.canInteract(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Leashing an animal in governed land requires authority.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getEntity().getLocation();
        if (!protection.canInteractWithEntity(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Unleashing an animal in governed land requires authority.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerUnleashEntity(PlayerUnleashEntityEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getEntity().getLocation();
        if (!protection.canInteractWithEntity(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * PvP / friendly-fire by territory, plus animal kill:
     * any player-caused damage (melee or projectile) in governed land is gated
     * by the attacker's authority. Player victims go through allowsPvp (self-
     * damage excluded). Claim-property entities — passive animals/pets
     * (Animals/Tameable), villagers, and armor stands — are protected like claim
     * builds; hostile mobs stay killable by everyone so owners can defend.
     * Uncontained / anarchy land stays unrestricted.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            damager = shooter;
        }
        if (!(damager instanceof Player attacker)) {
            return;
        }
        Entity victim = event.getEntity();
        if (!isClaimProperty(victim)) {
            return;
        }
        Location loc = victim.getLocation();
        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        if (victim instanceof Player victimPlayer) {
            if (!protection.allowsPvp(
                    world, x, z,
                    attacker.getUniqueId().toString(),
                    victimPlayer.getUniqueId().toString())) {
                event.setCancelled(true);
            }
        } else if (!protection.canInteract(
                world, x, z, attacker.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Whether damaging this entity is treated as damaging a claim's property:
     * players, passive animals and pets, villagers, and armor stands. Hostile
     * mobs are excluded so owners can kill them in their own territory.
     */
    private static boolean isClaimProperty(Entity entity) {
        return entity instanceof Player
                || entity instanceof org.bukkit.entity.Animals
                || entity instanceof org.bukkit.entity.Tameable
                || entity instanceof org.bukkit.entity.Villager
                || entity instanceof org.bukkit.entity.ArmorStand;
    }

    /**
     * Setting spawn (bed / respawn anchor / command) inside a governed territory.
     * Owners may set spawn in their own land; outsiders cannot claim it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerSetSpawn(PlayerSetSpawnEvent event) {
        Player player = event.getPlayer();
        Location spawn = event.getLocation();
        if (spawn == null) {
            return;
        }
        if (!protection.canTeleportInto(
                spawn.getWorld().getName(), spawn.getBlockX(), spawn.getBlockZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Sleeping in a bed inside governed land = interacting with the claim.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        Block bed = event.getBed();
        if (!protection.canInteract(
                bed.getWorld().getName(), bed.getX(), bed.getZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Forced teleports (commands, plugins, portals, pearls) into governed land.
     * Respawns to a player's own bed never fire this event, so spawn-based
     * respawns stay free.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!FORCED_TELEPORT_CAUSES.contains(event.getCause())) {
            return;
        }
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (!protection.canTeleportInto(
                to.getWorld().getName(), to.getBlockX(), to.getBlockZ(),
                player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    /**
     * Best-effort inventory location: holder block, else holder entity.
     */
    private static Location inventoryLocation(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockState blockState) {
            return blockState.getLocation();
        }
        if (holder instanceof Entity entity) {
            return entity.getLocation();
        }
        return null;
    }
}
