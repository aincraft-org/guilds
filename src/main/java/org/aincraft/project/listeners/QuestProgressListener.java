package org.aincraft.project.listeners;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.project.ProjectService;
import org.aincraft.project.QuestType;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

@Singleton
public class QuestProgressListener implements Listener {

    private final ProjectService projectService;
    private final GuildService guildService;

    @Inject
    public QuestProgressListener(ProjectService projectService, GuildService guildService) {
        this.projectService = Objects.requireNonNull(projectService);
        this.guildService = Objects.requireNonNull(guildService);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        Guild guild = guildService.getPlayerGuild(killer.getUniqueId());
        if (guild == null) return;

        String entityType = entity.getType().name();
        projectService.recordQuestProgress(guild.getId(), QuestType.KILL_MOB, entityType, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) return;

        String blockType = event.getBlock().getType().name();
        projectService.recordQuestProgress(guild.getId(), QuestType.MINE_BLOCK, blockType, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) return;

        ItemStack result = event.getRecipe().getResult();
        String itemType = result.getType().name();
        int amount = result.getAmount();

        // Handle shift-click crafting
        if (event.isShiftClick()) {
            int maxCrafts = calculateMaxCrafts(event);
            amount = result.getAmount() * maxCrafts;
        }

        projectService.recordQuestProgress(guild.getId(), QuestType.CRAFT_ITEM, itemType, amount);
    }

    private int calculateMaxCrafts(CraftItemEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        int maxCrafts = Integer.MAX_VALUE;

        for (ItemStack item : matrix) {
            if (item != null && item.getType() != Material.AIR) {
                int possibleCrafts = item.getAmount();
                if (possibleCrafts < maxCrafts) {
                    maxCrafts = possibleCrafts;
                }
            }
        }

        return maxCrafts == Integer.MAX_VALUE ? 1 : maxCrafts;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) return;

        ItemStack item = event.getItem().getItemStack();
        String itemType = item.getType().name();
        int amount = item.getAmount();

        projectService.recordQuestProgress(guild.getId(), QuestType.COLLECT_ITEM, itemType, amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) return;

        Entity caught = event.getCaught();
        if (caught == null) return;

        // For items, get the item type
        if (caught instanceof org.bukkit.entity.Item itemEntity) {
            String itemType = itemEntity.getItemStack().getType().name();
            int amount = itemEntity.getItemStack().getAmount();
            projectService.recordQuestProgress(guild.getId(), QuestType.FISH_ITEM, itemType, amount);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) return;

        String entityType = event.getEntity().getType().name();
        projectService.recordQuestProgress(guild.getId(), QuestType.BREED_MOB, entityType, 1);
    }
}
