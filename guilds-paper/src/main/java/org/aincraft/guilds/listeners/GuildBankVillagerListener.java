package org.aincraft.guilds.listeners;

import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.MintGuildBankService;
import org.aincraft.guilds.services.ResidentService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/** Opens the player's canonical guild bank account at a tagged villager. */
public final class GuildBankVillagerListener implements Listener {
    private final JavaPlugin plugin;
    private final GuildService guildService;
    private final ResidentService residentService;
    private volatile MintGuildBankService bank;
    private final String scoreboardTag;

    public GuildBankVillagerListener(JavaPlugin plugin, GuildService guildService,
                                     ResidentService residentService, MintGuildBankService bank,
                                     String scoreboardTag) {
        this.plugin = Objects.requireNonNull(plugin);
        this.guildService = Objects.requireNonNull(guildService);
        this.residentService = Objects.requireNonNull(residentService);
        this.bank = bank;
        this.scoreboardTag = scoreboardTag == null || scoreboardTag.isBlank() ? "GUILD_BANK" : scoreboardTag;
    }

    public void setBank(MintGuildBankService bank) { this.bank = bank; }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        MintGuildBankService currentBank = bank;
        if (!(event.getRightClicked() instanceof Villager villager)
                || !villager.getScoreboardTags().contains(scoreboardTag) || currentBank == null) return;
        Player player = event.getPlayer();
        var resident = residentService.getResident(player.getUniqueId());
        if (resident.isEmpty() || !resident.get().hasGuild()) return;
        var guild = guildService.getGuild(resident.get().getGuild());
        if (guild.isEmpty()) return;
        event.setCancelled(true);
        currentBank.openAccount(player.getUniqueId(), guild.get().getId()).whenComplete((result, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null || result == null) player.sendMessage(ChatColor.RED + "Mint guild bank is unavailable.");
                    else if (result.status() == MintGuildBankService.Status.COMMITTED) player.sendMessage(ChatColor.GREEN + "Guild bank account opened.");
                    else if (result.status() == MintGuildBankService.Status.UNAUTHORIZED) player.sendMessage(ChatColor.RED + "You are not a current guild member.");
                    else player.sendMessage(ChatColor.RED + "Mint guild bank is unavailable.");
                }));
    }
}
