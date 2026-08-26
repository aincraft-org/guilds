package dev.mintychochip.guilds.listeners;

import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.guilds.services.MintGuildBankService;
import dev.mintychochip.guilds.services.ResidentService;
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
    /** The plugin. */
    private final JavaPlugin plugin;
    /** The guild service. */
    private final GuildService guildService;
    /** The resident service. */
    private final ResidentService residentService;
    /** The bank. */
    private volatile MintGuildBankService bank;
    /** The scoreboard tag. */
    private final String scoreboardTag;

    /**
     * Creates a new guild bank villager listener instance.
     * @param plugin the plugin
     * @param guildService the guild service
     * @param residentService the resident service
     * @param bank the bank
     * @param scoreboardTag the scoreboard tag
     */
    public GuildBankVillagerListener(JavaPlugin plugin, GuildService guildService,
                                     ResidentService residentService, MintGuildBankService bank,
                                     String scoreboardTag) {
        this.plugin = Objects.requireNonNull(plugin);
        this.guildService = Objects.requireNonNull(guildService);
        this.residentService = Objects.requireNonNull(residentService);
        this.bank = bank;
        this.scoreboardTag = scoreboardTag == null || scoreboardTag.isBlank() ? "GUILD_BANK" : scoreboardTag;
    }

    /**
     * Sets the bank.
     * @param bank the bank
     */
    public void setBank(MintGuildBankService bank) { this.bank = bank; }

    /**
     * Handles the player interact entity.
     * @param event the event
     */
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
