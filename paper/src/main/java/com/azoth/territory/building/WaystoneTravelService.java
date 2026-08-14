package com.azoth.territory.building;

import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.permission.BlockProtection;
import com.azoth.territory.registry.FacilityRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class WaystoneTravelService {
    private final JavaPlugin plugin;
    private final FacilityRegistry facilities;
    private final FacilityAnchorValidator anchors;
    private final WaystoneAccess access;
    private final SafeLandingResolver landings;
    private final BlockProtection protection;
    private final BuildingConfig config;
    private final Map<UUID, PendingTravel> pending = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public WaystoneTravelService(JavaPlugin plugin, FacilityRegistry facilities,
                                 FacilityAnchorValidator anchors, WaystoneAccess access,
                                 SafeLandingResolver landings, BlockProtection protection,
                                 BuildingConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.facilities = Objects.requireNonNull(facilities, "facilities");
        this.anchors = Objects.requireNonNull(anchors, "anchors");
        this.access = Objects.requireNonNull(access, "access");
        this.landings = Objects.requireNonNull(landings, "landings");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.config = Objects.requireNonNull(config, "config");
    }

    public StartResult start(Player player, SettlementFacility origin, String destinationId, long nowMillis) {
        if (remainingCooldownMillis(player.getUniqueId(), nowMillis) > 0L) return StartResult.COOLDOWN;
        if (origin == null || !anchors.validate(origin).active()) return StartResult.INVALID_ORIGIN;
        SettlementFacility destination = access.reachable(player.getUniqueId(), origin).stream()
                .filter(candidate -> candidate.id().equals(destinationId)).findFirst().orElse(null);
        if (destination == null) return StartResult.INACCESSIBLE_DESTINATION;
        Location landing = landings.find(destination).orElse(null);
        if (landing == null) return StartResult.NO_SAFE_LANDING;
        if (!protection.canTeleportInto(destination.worldId(), landing.getBlockX(), landing.getBlockZ(),
                player.getUniqueId().toString())) return StartResult.PROTECTED_DESTINATION;
        cancel(player.getUniqueId(), CancelReason.REPLACED);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> complete(player.getUniqueId()), config.waystoneWarmupTicks());
        pending.put(player.getUniqueId(), new PendingTravel(origin.id(), destination.id(), task));
        return StartResult.STARTED;
    }

    private void complete(UUID playerId) {
        PendingTravel travel = pending.remove(playerId);
        if (travel == null) return;
        Player player = plugin.getServer().getPlayer(playerId);
        SettlementFacility origin = facilities.get(travel.originId()).orElse(null);
        SettlementFacility destination = facilities.get(travel.destinationId()).orElse(null);
        if (player == null || origin == null || destination == null
                || access.reachable(playerId, origin).stream().noneMatch(f -> f.id().equals(destination.id()))) return;
        Location landing = landings.find(destination).orElse(null);
        if (landing == null || !protection.canTeleportInto(destination.worldId(), landing.getBlockX(),
                landing.getBlockZ(), playerId.toString())) return;
        if (player.teleport(landing)) {
            cooldowns.put(playerId, Math.addExact(System.currentTimeMillis(), config.waystoneCooldownMillis()));
        }
    }

    public void cancel(UUID playerId, CancelReason reason) {
        PendingTravel travel = pending.remove(playerId);
        if (travel != null) travel.task().cancel();
    }

    public long remainingCooldownMillis(UUID playerId, long nowMillis) {
        return Math.max(0L, cooldowns.getOrDefault(playerId, 0L) - nowMillis);
    }

    public boolean isPending(UUID playerId) {
        return pending.containsKey(playerId);
    }

    public void stop() {
        pending.values().forEach(travel -> travel.task().cancel());
        pending.clear();
    }

    public enum StartResult {
        STARTED, COOLDOWN, INVALID_ORIGIN, INACCESSIBLE_DESTINATION,
        NO_SAFE_LANDING, PROTECTED_DESTINATION
    }

    public enum CancelReason { MOVED, DAMAGED, DIED, QUIT, REPLACED, SHUTDOWN }

    private record PendingTravel(String originId, String destinationId, BukkitTask task) { }
}
