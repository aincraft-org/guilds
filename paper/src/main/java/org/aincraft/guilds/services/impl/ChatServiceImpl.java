package org.aincraft.guilds.services.impl;



import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.ChatService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of ChatService for managing guild chat functionality
 */

public class ChatServiceImpl implements ChatService {

    private final JavaPlugin plugin;
    private final GuildService guildService;
    private final ResidentService residentService;

    // Store chat preferences in memory
    private final Map<UUID, Boolean> guildChatToggle = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> adminSpyToggle = new ConcurrentHashMap<>();


    public ChatServiceImpl(JavaPlugin plugin, GuildService guildService, ResidentService residentService) {
        this.plugin = plugin;
        this.guildService = guildService;
        this.residentService = residentService;
    }

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

    @Override
    public boolean isGuildChatEnabled(UUID playerUuid) {
        return guildChatToggle.getOrDefault(playerUuid, false);
    }

    @Override
    public void setGuildChatEnabled(UUID playerUuid, boolean enabled) {
        guildChatToggle.put(playerUuid, enabled);
    }

    @Override
    public boolean isAdminSpy(UUID playerUuid) {
        return adminSpyToggle.getOrDefault(playerUuid, false);
    }

    @Override
    public void setAdminSpy(UUID playerUuid, boolean enabled) {
        adminSpyToggle.put(playerUuid, enabled);
    }
}