package dev.mintychochip.guilds.services.impl;

import dev.mintychochip.territory.permission.BlockProtection;
import dev.mintychochip.guilds.services.GuildHearthstoneService;
import dev.mintychochip.guilds.services.GuildService;
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

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The guild service. */
    private final GuildService guildService;
    /** The block protection. */
    private final BlockProtection blockProtection;
    /** The cooldown seconds. */
    private final long cooldownSeconds;
    /** The cooldowns. */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * Creates a new guild hearthstone service impl instance.
     * @param plugin the plugin
     * @param guildService the guild service
     * @param blockProtection the block protection
     * @param cooldownSeconds the cooldown seconds
     */
    public GuildHearthstoneServiceImpl(JavaPlugin plugin, GuildService guildService,
                                       BlockProtection blockProtection, long cooldownSeconds) {
        this.plugin = plugin;
        this.guildService = guildService;
        this.blockProtection = blockProtection;
        this.cooldownSeconds = cooldownSeconds;
    }

    /**
     * Performs the teleport to guild spawn operation.
     * @param playerUuid the player uuid
     * @return the result
     */
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
        dev.mintychochip.guilds.models.Location model = spawnOpt.get();
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

    /**
     * Performs the remaining cooldown seconds operation.
     * @param playerUuid the player uuid
     * @return the result
     */
    @Override
    public long remainingCooldownSeconds(UUID playerUuid) {
        Long expiry = cooldowns.get(playerUuid);
        if (expiry == null) return 0;
        long now = System.currentTimeMillis() / 1000;
        long secs = expiry - now;
        return secs > 0 ? secs : 0;
    }

    /**
     * Sets the cooldown.
     * @param playerUuid the player uuid
     * @param seconds the seconds
     */
    @Override
    public void setCooldown(UUID playerUuid, long seconds) {
        if (playerUuid == null) return;
        long expiry = System.currentTimeMillis() / 1000 + seconds;
        cooldowns.put(playerUuid, expiry);
    }

    /**
     * Performs the guild name of operation.
     * @param player the player
     * @return the result
     */
    private String guildNameOf(Player player) {
        for (dev.mintychochip.guilds.models.Guild guild : guildService.getAllGuilds()) {
            if (guild.isResident(player.getUniqueId())) {
                return guild.getName();
            }
        }
        return null;
    }
}
