package org.aincraft.guilds.services.impl;

import dev.mintychochip.territory.permission.BlockProtection;
import org.aincraft.guilds.services.GuildHearthstoneService;
import org.aincraft.guilds.services.GuildService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hearthstone teleport to guild spawn with cooldown and territory safety checks.
 * <p>
 * Uses existing {@link GuildService#getGuildSpawn(String)} and
 * {@link GuildService#canTeleportToSpawn(UUID, String)} APIs.
 */
public class GuildHearthstoneServiceImpl implements GuildHearthstoneService {

    private final JavaPlugin plugin;
    private final GuildService guildService;
    private final BlockProtection blockProtection;
    private final long cooldownSeconds;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public GuildHearthstoneServiceImpl(JavaPlugin plugin, GuildService guildService,
                                       BlockProtection blockProtection, long cooldownSeconds) {
        this.plugin = plugin;
        this.guildService = guildService;
        this.blockProtection = blockProtection;
        this.cooldownSeconds = cooldownSeconds;
    }

    @Override
    public boolean teleportToGuildSpawn(UUID playerUuid) {
        if (playerUuid == null) return false;
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return false;
        }
        long remaining = remainingCooldownSeconds(playerUuid);
        if (remaining > 0) {
            player.sendMessage("§cHearthstone is on cooldown for " + remaining + "s.");
            return false;
        }
        String guildName = guildNameOf(player);
        if (guildName == null) {
            player.sendMessage("§cYou are not in a guild.");
            return false;
        }
        if (!guildService.canTeleportToSpawn(playerUuid, guildName)) {
            player.sendMessage("§cYou cannot teleport to your guild spawn.");
            return false;
        }
        var spawnOpt = guildService.getGuildSpawn(guildName);
        if (spawnOpt.isEmpty()) {
            player.sendMessage("§cGuild spawn is not set.");
            return false;
        }
        org.aincraft.guilds.models.Location model = spawnOpt.get();
        org.bukkit.World world = plugin.getServer().getWorld(model.getWorld());
        if (world == null) {
            player.sendMessage("§cGuild spawn world is not loaded.");
            return false;
        }
        org.bukkit.Location bukkit = new org.bukkit.Location(
                world,
                model.getX(),
                model.getY(),
                model.getZ(),
                (float) model.getYaw(),
                (float) model.getPitch()
        );
        if (!blockProtection.canTeleportInto(
                bukkit.getWorld().getName(),
                bukkit.getBlockX(), bukkit.getBlockZ(),
                playerUuid.toString())) {
            player.sendMessage("§cDestination is protected.");
            return false;
        }
        if (!player.teleport(bukkit)) {
            return false;
        }
        setCooldown(playerUuid, cooldownSeconds);
        return true;
    }

    @Override
    public long remainingCooldownSeconds(UUID playerUuid) {
        Long expiry = cooldowns.get(playerUuid);
        if (expiry == null) return 0;
        long now = System.currentTimeMillis() / 1000;
        long secs = expiry - now;
        return secs > 0 ? secs : 0;
    }

    @Override
    public void setCooldown(UUID playerUuid, long seconds) {
        if (playerUuid == null) return;
        long expiry = System.currentTimeMillis() / 1000 + seconds;
        cooldowns.put(playerUuid, expiry);
    }

    private String guildNameOf(Player player) {
        for (org.aincraft.guilds.models.Guild guild : guildService.getAllGuilds()) {
            if (guild.isResident(player.getUniqueId())) {
                return guild.getName();
            }
        }
        return null;
    }
}
