package org.aincraft.towny.examples;

import org.aincraft.towny.plot.PlotTypeDefinition;
import org.aincraft.towny.plot.PlotTypeHandler;
import org.aincraft.towny.events.plot.PlotTypeEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Example plugin demonstrating how to use the extensible plot type system
 * This example creates enhanced farm plots with special mechanics
 */
public class FarmPlotPluginExample implements PlotTypeHandler, Listener {

    private static final String PLUGIN_NAME = "FarmPlotExample";
    private final PlotTypeDefinition farmPlotType;

    public FarmPlotPluginExample() {
        // Create the enhanced farm plot type definition
        this.farmPlotType = PlotTypeDefinition.builder()
                .typeName("enhanced_farm")
                .displayName("Enhanced Farm")
                .description("Agricultural plot with crop growth bonuses and special mechanics")
                .pluginName(PLUGIN_NAME)
                .metadata("growth_multiplier", 1.5)
                .metadata("auto_harvest", true)
                .metadata("fertilizer_bonus", 2.0)
                .metadata("seasonal_effects", true)
                .requirePermission("farmplot.use")
                .build();
    }

    /**
     * Get the plot type definition for registration
     */
    public PlotTypeDefinition getPlotTypeDefinition() {
        return farmPlotType;
    }

    @Override
    public void onPlayerEnter(PlotTypeEvent.PlayerEnterPlotEvent event) {
        Player player = event.getPlayer();

        // Send welcome message when entering enhanced farm
        if (player.hasPermission("farmplot.bonus")) {
            double growthMultiplier = farmPlotType.getMetadata("growth_multiplier", Double.class, 1.0);
            player.sendMessage("§aEntering Enhanced Farm! §eCrop growth bonus: §b" +
                             (int)((growthMultiplier - 1) * 100) + "%");
        }

        // Apply seasonal effects if enabled
        if (farmPlotType.getMetadata("seasonal_effects", Boolean.class, false)) {
            applySeasonalEffect(player);
        }
    }

    @Override
    public void onPlayerLeave(PlotTypeEvent.PlayerLeavePlotEvent event) {
        Player player = event.getPlayer();
        player.sendMessage("§7Leaving Enhanced Farm plot");
    }

    @Override
    public void onPlotAction(PlotTypeEvent.PlotActionEvent event) {
        Player player = event.getPlayer();
        String action = event.getAction();

        switch (action) {
            case "plant":
                handlePlanting(event);
                break;
            case "harvest":
                handleHarvesting(event);
                break;
            case "fertilize":
                handleFertilizing(event);
                break;
            default:
                break;
        }
    }

    @Override
    public void onPlotTypeChange(PlotTypeEvent.PlotTypeChangeEvent event) {
        Player player = event.getPlayer();

        if (event.getNewType().equals("enhanced_farm")) {
            player.sendMessage("§aPlot converted to Enhanced Farm! Special farm mechanics activated.");
        } else if (event.getOldType().equals("enhanced_farm")) {
            player.sendMessage("§cEnhanced Farm plot type removed - farm mechanics deactivated.");
        }
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public String[] getHandledPlotTypes() {
        return new String[]{"enhanced_farm"};
    }

    /**
     * Handle planting actions with growth bonuses
     */
    private void handlePlanting(PlotTypeEvent.PlotActionEvent event) {
        Player player = event.getPlayer();
        ItemStack seeds = event.getActionData(ItemStack.class);

        if (seeds != null && isCropSeed(seeds.getType())) {
            double growthMultiplier = farmPlotType.getMetadata("growth_multiplier", Double.class, 1.0);

            // Apply growth bonus effect (visual feedback for example)
            if (growthMultiplier > 1.0) {
                player.sendMessage("§eGrowth bonus applied! §7(×" + growthMultiplier + ")");

                // In a real implementation, you would modify the crop growth rate here
                // This could involve setting metadata on the block, using NBT, or other mechanisms
            }
        }
    }

    /**
     * Handle harvesting with auto-harvest feature
     */
    private void handleHarvesting(PlotTypeEvent.PlotActionEvent event) {
        Player player = event.getPlayer();

        boolean autoHarvest = farmPlotType.getMetadata("auto_harvest", Boolean.class, false);
        if (autoHarvest && player.hasPermission("farmplot.autoharvest")) {
            // Auto-harvest logic would go here
            player.sendMessage("§6Auto-harvest activated! Nearby crops harvested automatically.");
        }
    }

    /**
     * Handle fertilizing with bonus effects
     */
    private void handleFertilizing(PlotTypeEvent.PlotActionEvent event) {
        Player player = event.getPlayer();

        double fertilizerBonus = farmPlotType.getMetadata("fertilizer_bonus", Double.class, 1.0);
        if (fertilizerBonus > 1.0) {
            player.sendMessage("§2Super fertilizer applied! §7Growth boost: §a×" + fertilizerBonus);
        }
    }

    /**
     * Apply seasonal effects for immersive farming
     */
    private void applySeasonalEffect(Player player) {
        // In a real implementation, you would check the actual season/time
        // For this example, we'll simulate seasonal effects

        String season = getCurrentSeason();
        switch (season) {
            case "spring":
                player.sendMessage("§a🌸 Spring Season: §e+25% crop growth rate");
                break;
            case "summer":
                player.sendMessage("§6☀️ Summer Season: §e+50% crop yield but -25% water efficiency");
                break;
            case "fall":
                player.sendMessage("§c🍂 Fall Season: §e+35% harvest value");
                break;
            case "winter":
                player.sendMessage("§b❄️ Winter Season: §eCrops grow slower but protected from frost");
                break;
        }
    }

    /**
     * Check if an item is a crop seed
     */
    private boolean isCropSeed(Material material) {
        return material == Material.WHEAT_SEEDS ||
               material == Material.CARROT ||
               material == Material.POTATO ||
               material == Material.BEETROOT_SEEDS ||
               material == Material.PUMPKIN_SEEDS ||
               material == Material.MELON_SEEDS;
    }

    /**
     * Get current season (simplified for example)
     */
    private String getCurrentSeason() {
        // In a real implementation, you'd calculate based on world time or configuration
        long time = System.currentTimeMillis() / 1000;
        int seasonIndex = (int) ((time / (30 * 24 * 60 * 60)) % 4);

        String[] seasons = {"spring", "summer", "fall", "winter"};
        return seasons[seasonIndex];
    }

    // Bukkit event listeners for enhanced farm mechanics

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Handle special farm block placements
        if (event.getBlock().getType() == Material.FARMLAND) {
            // Enhanced farmland could have better durability or water retention
            event.getPlayer().sendMessage("§eEnhanced farmland placed! Better water retention.");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
            event.getClickedBlock() != null &&
            event.getClickedBlock().getType() == Material.FARMLAND) {

            Player player = event.getPlayer();
            if (player.getInventory().getItemInMainHand().getType() == Material.BONE_MEAL) {
                // Enhanced fertilizing
                player.sendMessage("§2Super fertilizer effect applied!");
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // Enhanced harvesting
        if (event.getBlock().getType() == Material.WHEAT) {
            org.bukkit.block.data.Ageable ageData = (org.bukkit.block.data.Ageable) event.getBlock().getBlockData();
            if (ageData.getAge() == ageData.getMaximumAge()) { // Fully grown wheat
                // Bonus drops from enhanced farming
                if (Math.random() < 0.3) { // 30% chance for bonus seeds
                    event.getBlock().getWorld().dropItem(event.getBlock().getLocation(),
                                                      new ItemStack(Material.WHEAT_SEEDS, 2));
                }
            }
        }
    }

    /**
     * Static factory method for easy plugin creation
     */
    public static FarmPlotPluginExample create() {
        return new FarmPlotPluginExample();
    }
}