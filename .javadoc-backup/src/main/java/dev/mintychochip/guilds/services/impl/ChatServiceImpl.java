package dev.mintychochip.guilds.services.impl;



import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.ChatService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.GuildService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of ChatService for managing guild chat functionality
 */

public class ChatServiceImpl implements ChatService {

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The guild service. */
    private final GuildService guildService;
    /** The resident service. */
    private final ResidentService residentService;

    // Store chat preferences in memory
    /** The guild chat toggle. */
    private final Map<UUID, Boolean> guildChatToggle = new ConcurrentHashMap<>();
    /** The admin spy toggle. */
    private final Map<UUID, Boolean> adminSpyToggle = new ConcurrentHashMap<>();


    /**
     * Creates a new chat service impl instance.
     * @param plugin the plugin
     * @param guildService the guild service
     * @param residentService the resident service
     */
    public ChatServiceImpl(JavaPlugin plugin, GuildService guildService, ResidentService residentService) {
        this.plugin = plugin;
        this.guildService = guildService;
        this.residentService = residentService;
    }

    /**
     * Performs the send guild chat operation.
     * @param guildId the guild id
     * @param sender the sender
     * @param message the message
     */
    @Override
    public void sendGuildChat(String guildId, Player sender, String message) {
        Guild guild = guildService.getGuildById(guildId).orElse(null);
        if (guild == null) {
            return;
        }

        // Format message using Guilds chat format
        String guildName = guild.getName();
        String playerName = sender.getName();

        Component guildChatComponent = Component.text("[GuildChat] ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("[" + guildName + "] ")
                        .color(NamedTextColor.GREEN))
                .append(Component.text(playerName + ": ")
                        .color(NamedTextColor.WHITE))
                .append(Component.text(message)
                        .color(NamedTextColor.GRAY));

        // Send to all online residents
        int sentCount = 0;
        for (UUID residentUuid : guild.getResidents()) {
            Player resident = Bukkit.getPlayer(residentUuid);
            if (resident != null && resident.isOnline()) {
                resident.sendMessage(guildChatComponent);
                sentCount++;
            }
        }

        // Also send to admins with spy enabled
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (isAdminSpy(onlinePlayer.getUniqueId()) &&
                !guild.getResidents().contains(onlinePlayer.getUniqueId())) {
                onlinePlayer.sendMessage(guildChatComponent);
                sentCount++;
            }
        }

        if (plugin.isEnabled()) {
            plugin.getLogger().info("Sent guild chat from " + playerName + " to " + sentCount + " recipients in guild " + guildName);
        }
    }

    /**
     * Returns whether guild chat enabled.
     * @param playerUuid the player uuid
     * @return the result
     */
    @Override
    public boolean isGuildChatEnabled(UUID playerUuid) {
        return guildChatToggle.getOrDefault(playerUuid, false);
    }

    /**
     * Sets the guild chat enabled.
     * @param playerUuid the player uuid
     * @param enabled the enabled
     */
    @Override
    public void setGuildChatEnabled(UUID playerUuid, boolean enabled) {
        guildChatToggle.put(playerUuid, enabled);
    }

    /**
     * Returns whether admin spy.
     * @param playerUuid the player uuid
     * @return the result
     */
    @Override
    public boolean isAdminSpy(UUID playerUuid) {
        return adminSpyToggle.getOrDefault(playerUuid, false);
    }

    /**
     * Sets the admin spy.
     * @param playerUuid the player uuid
     * @param enabled the enabled
     */
    @Override
    public void setAdminSpy(UUID playerUuid, boolean enabled) {
        adminSpyToggle.put(playerUuid, enabled);
    }
}