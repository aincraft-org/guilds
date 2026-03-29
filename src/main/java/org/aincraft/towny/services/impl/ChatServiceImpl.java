package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.ChatService;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of ChatService for managing town chat functionality
 */
@Singleton
public class ChatServiceImpl implements ChatService {

    private final TownyPlugin plugin;
    private final TownService townService;
    private final ResidentService residentService;

    // Store chat preferences in memory
    private final Map<UUID, Boolean> townChatToggle = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> adminSpyToggle = new ConcurrentHashMap<>();

    @Inject
    public ChatServiceImpl(TownyPlugin plugin, TownService townService, ResidentService residentService) {
        this.plugin = plugin;
        this.townService = townService;
        this.residentService = residentService;
    }

    @Override
    public void sendTownChat(String townId, Player sender, String message) {
        Town town = townService.getTownById(townId).orElse(null);
        if (town == null) {
            return;
        }

        // Format message using Towny chat format
        String townName = town.getName();
        String playerName = sender.getName();
        
        Component townChatComponent = Component.text("[TownChat] ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("[" + townName + "] ")
                        .color(NamedTextColor.GREEN))
                .append(Component.text(playerName + ": ")
                        .color(NamedTextColor.WHITE))
                .append(Component.text(message)
                        .color(NamedTextColor.GRAY));

        // Send to all online residents
        int sentCount = 0;
        for (UUID residentUuid : town.getResidents()) {
            Player resident = Bukkit.getPlayer(residentUuid);
            if (resident != null && resident.isOnline()) {
                resident.sendMessage(townChatComponent);
                sentCount++;
            }
        }

        // Also send to admins with spy enabled
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (isAdminSpy(onlinePlayer.getUniqueId()) && 
                !town.getResidents().contains(onlinePlayer.getUniqueId())) {
                onlinePlayer.sendMessage(townChatComponent);
                sentCount++;
            }
        }

        if (plugin.isEnabled()) {
            plugin.getLogger().info("Sent town chat from " + playerName + " to " + sentCount + " recipients in town " + townName);
        }
    }

    @Override
    public boolean isTownChatEnabled(UUID playerUuid) {
        return townChatToggle.getOrDefault(playerUuid, false);
    }

    @Override
    public void setTownChatEnabled(UUID playerUuid, boolean enabled) {
        townChatToggle.put(playerUuid, enabled);
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