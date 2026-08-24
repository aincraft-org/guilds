package org.aincraft.guilds.storage.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Resolves online player block locations through Bukkit. */
public final class BukkitPlayerLocationSource implements PlayerLocationSource {
    @Override
    public Optional<BlockLocation> locationOf(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return Optional.empty();
        }
        var location = player.getLocation();
        if (location.getWorld() == null) {
            return Optional.empty();
        }
        return Optional.of(new BlockLocation(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()));
    }
}
