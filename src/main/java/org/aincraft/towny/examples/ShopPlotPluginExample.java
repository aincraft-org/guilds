package org.aincraft.towny.examples;

import org.aincraft.towny.plot.PlotTypeDefinition;
import org.aincraft.towny.plot.PlotTypeHandler;
import org.aincraft.towny.events.plot.PlotTypeEvent;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Example plugin for shop plots with marketplace functionality
 * Demonstrates how to create commercial plot types with special features
 */
public class ShopPlotPluginExample implements PlotTypeHandler, Listener {

    private static final String PLUGIN_NAME = "ShopPlotExample";
    private final PlotTypeDefinition shopPlotType;

    // Shop registry to store plot-specific shop configurations
    private final Map<String, ShopConfiguration> shopRegistry = new HashMap<>();

    public ShopPlotPluginExample() {
        this.shopPlotType = PlotTypeDefinition.builder()
                .typeName("premium_shop")
                .displayName("Premium Shop")
                .description("Commercial plot with advanced marketplace features and customer attraction")
                .pluginName(PLUGIN_NAME)
                .metadata("shop_type", "premium")
                .metadata("max_stalls", 8)
                .metadata("tax_rate", 0.05)
                .metadata("advertising_enabled", true)
                .metadata("customer_attraction", true)
                .requirePermission("shopplot.premium")
                .build();
    }

    /**
     * Get the plot type definition for registration
     */
    public PlotTypeDefinition getPlotTypeDefinition() {
        return shopPlotType;
    }

    @Override
    public void onPlayerEnter(PlotTypeEvent.PlayerEnterPlotEvent event) {
        Player player = event.getPlayer();
        String plotId = event.getPlot().getId().toString();

        // Check if this plot has a shop configuration
        ShopConfiguration shopConfig = shopRegistry.get(plotId);
        if (shopConfig != null) {
            showShopWelcome(player, shopConfig);
        } else {
            // Default shop experience
            player.sendMessage("§6Welcome to Premium Shop Plot!");
            player.sendMessage("§7Right-click a chest to set up shop, or use §e/shop setup§7 to configure.");
        }

        // Apply customer attraction effect if enabled
        if (shopPlotType.getMetadata("customer_attraction", Boolean.class, false)) {
            applyCustomerAttraction(player);
        }
    }

    @Override
    public void onPlayerLeave(PlotTypeEvent.PlayerLeavePlotEvent event) {
        Player player = event.getPlayer();
        player.sendMessage("§7Left Premium Shop area");
    }

    @Override
    public void onPlotAction(PlotTypeEvent.PlotActionEvent event) {
        Player player = event.getPlayer();
        String action = event.getAction();

        switch (action) {
            case "open_shop":
                handleShopOpen(event);
                break;
            case "close_shop":
                handleShopClose(event);
                break;
            case "advertise":
                handleAdvertisement(event);
                break;
            case "transaction":
                handleTransaction(event);
                break;
            default:
                break;
        }
    }

    @Override
    public void onPlotTypeChange(PlotTypeEvent.PlotTypeChangeEvent event) {
        Player player = event.getPlayer();

        if (event.getNewType().equals("premium_shop")) {
            player.sendMessage("§aPremium Shop plot created! Use §e/shop setup§7 to configure your shop.");
            initializeShopConfiguration(event.getPlot().getId().toString());
        } else if (event.getOldType().equals("premium_shop")) {
            cleanupShopConfiguration(event.getPlot().getId().toString());
            player.sendMessage("§cPremium Shop plot removed - shop configuration cleaned up.");
        }
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public String[] getHandledPlotTypes() {
        return new String[]{"premium_shop"};
    }

    /**
     * Initialize shop configuration for a new plot
     */
    private void initializeShopConfiguration(String plotId) {
        ShopConfiguration config = new ShopConfiguration(plotId);
        config.setMaxStalls(shopPlotType.getMetadata("max_stalls", Integer.class, 8));
        config.setTaxRate(shopPlotType.getMetadata("tax_rate", Double.class, 0.05));
        config.setAdvertisingEnabled(shopPlotType.getMetadata("advertising_enabled", Boolean.class, true));

        shopRegistry.put(plotId, config);
    }

    /**
     * Clean up shop configuration when plot type changes
     */
    private void cleanupShopConfiguration(String plotId) {
        ShopConfiguration config = shopRegistry.remove(plotId);
        if (config != null) {
            // Clean up any ongoing shop activities
            config.closeShop();
        }
    }

    /**
     * Show welcome message for shop configuration
     */
    private void showShopWelcome(Player player, ShopConfiguration config) {
        if (config.isOpen()) {
            player.sendMessage("§6§l" + config.getShopName() + " §7- §aOpen for Business!");
            player.sendMessage("§7Owner: §e" + config.getOwnerName());
            player.sendMessage("§7Stalls: §a" + config.getUsedStalls() + "§7/§f" + config.getMaxStalls());

            if (config.hasSpecialDeal()) {
                player.sendMessage("§6§lSpecial Deal: §r" + config.getSpecialDeal());
            }
        } else {
            player.sendMessage("§6" + config.getShopName() + " §7- §cCurrently Closed");
        }
    }

    /**
     * Apply customer attraction effects (visual/immersive features)
     */
    private void applyCustomerAttraction(Player player) {
        // In a real implementation, you might:
        // - Play shop bell sounds
        // - Show particle effects
        // - Display holographic advertisements
        // - Apply temporary potion effects

        player.sendMessage("§e✨ Shop attraction effects activated!");

        // Example: Give players a slight speed boost in shop areas
        // player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
    }

    /**
     * Handle shop opening
     */
    private void handleShopOpen(PlotTypeEvent.PlotActionEvent event) {
        Player player = event.getPlayer();
        String plotId = event.getPlot().getId().toString();

        ShopConfiguration config = shopRegistry.get(plotId);
        if (config != null) {
            config.openShop(player);
            player.sendMessage("§aShop opened successfully!");

            // Trigger advertisement if enabled
            if (config.isAdvertisingEnabled()) {
                broadcastAdvertisement(player, config);
            }
        }
    }

    /**
     * Handle shop closing
     */
    private void handleShopClose(PlotTypeEvent.PlotActionEvent event) {
        Player player = event.getPlayer();
        String plotId = event.getPlot().getId().toString();

        ShopConfiguration config = shopRegistry.get(plotId);
        if (config != null) {
            config.closeShop();
            player.sendMessage("§cShop closed. Thanks for your business!");
        }
    }

    /**
     * Handle shop advertising
     */
    private void handleAdvertisement(PlotTypeEvent.PlotActionEvent event) {
        Player player = event.getPlayer();
        String plotId = event.getPlot().getId().toString();

        ShopConfiguration config = shopRegistry.get(plotId);
        if (config != null) {
            String advertisement = event.getActionData(String.class);
            config.setAdvertisement(advertisement);
            broadcastAdvertisement(player, config);
            player.sendMessage("§aAdvertisement broadcasted to nearby players!");
        }
    }

    /**
     * Handle shop transactions
     */
    private void handleTransaction(PlotTypeEvent.PlotActionEvent event) {
        Player player = event.getPlayer();
        TransactionData transaction = event.getActionData(TransactionData.class);

        if (transaction != null) {
            // Apply tax if configured
            double taxRate = shopPlotType.getMetadata("tax_rate", Double.class, 0.0);
            double taxAmount = transaction.getAmount() * taxRate;

            if (taxAmount > 0) {
                player.sendMessage("§7Transaction tax: §c" + String.format("%.2f", taxAmount));
                // In a real implementation, you'd handle the tax collection here
            }

            player.sendMessage("§aTransaction completed: " + transaction.getItemName() +
                             " ×" + transaction.getQuantity() + " for §e" +
                             String.format("%.2f", transaction.getAmount()));
        }
    }

    /**
     * Broadcast advertisement to nearby players
     */
    private void broadcastAdvertisement(Player owner, ShopConfiguration config) {
        String message = "§6[SHOP] §e" + config.getShopName() + " §7- §f" + config.getAdvertisement();

        // In a real implementation, you'd broadcast to players within a certain radius
        owner.getWorld().getPlayers().forEach(player -> {
            if (player.getLocation().distance(owner.getLocation()) <= 50) {
                player.sendMessage(message);
            }
        });
    }

    // Bukkit event listeners for shop interactions

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null &&
            event.getClickedBlock().getType() == Material.CHEST &&
            event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {

            Player player = event.getPlayer();

            // Check if player is in a premium shop plot
            // In a real implementation, you'd check the plot type and permissions
            if (player.hasPermission("shopplot.manage")) {
                player.sendMessage("§eRight-click to open shop configuration menu.");
                // In a real implementation, you'd open a custom GUI here
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Handle shop inventory interactions
        Player player = (Player) event.getWhoClicked();

        if (event.getView().getTitle().startsWith("Shop: ")) {
            // Cancel the event to prevent normal inventory behavior
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                // Handle shop item purchase/sale
                handleShopItemClick(player, clicked, event.getSlot());
            }
        }
    }

    /**
     * Handle clicks in shop inventory
     */
    private void handleShopItemClick(Player player, ItemStack item, int slot) {
        String itemName = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : item.getType().name();
        player.sendMessage("§eSelected: " + itemName + " §7(Quantity: " + item.getAmount() + ")");

        // In a real implementation, you would:
        // - Check player balance/inventory
        // - Process the transaction
        // - Update shop inventory
        // - Handle taxes and fees
    }

    /**
     * Shop configuration data class
     */
    public static class ShopConfiguration {
        private final String plotId;
        private String shopName = "Unnamed Shop";
        private String ownerName = "Unknown";
        private int maxStalls = 8;
        private int usedStalls = 0;
        private double taxRate = 0.05;
        private boolean isOpen = false;
        private boolean advertisingEnabled = true;
        private String advertisement = "Welcome to our shop!";
        private String specialDeal = null;

        public ShopConfiguration(String plotId) {
            this.plotId = plotId;
        }

        // Getters and setters
        public String getPlotId() { return plotId; }
        public String getShopName() { return shopName; }
        public void setShopName(String shopName) { this.shopName = shopName; }
        public String getOwnerName() { return ownerName; }
        public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
        public int getMaxStalls() { return maxStalls; }
        public void setMaxStalls(int maxStalls) { this.maxStalls = maxStalls; }
        public int getUsedStalls() { return usedStalls; }
        public void setUsedStalls(int usedStalls) { this.usedStalls = usedStalls; }
        public double getTaxRate() { return taxRate; }
        public void setTaxRate(double taxRate) { this.taxRate = taxRate; }
        public boolean isOpen() { return isOpen; }
        public void openShop(Player owner) { this.isOpen = true; }
        public void closeShop() { this.isOpen = false; }
        public boolean isAdvertisingEnabled() { return advertisingEnabled; }
        public void setAdvertisingEnabled(boolean advertisingEnabled) { this.advertisingEnabled = advertisingEnabled; }
        public String getAdvertisement() { return advertisement; }
        public void setAdvertisement(String advertisement) { this.advertisement = advertisement; }
        public String getSpecialDeal() { return specialDeal; }
        public void setSpecialDeal(String specialDeal) { this.specialDeal = specialDeal; }
        public boolean hasSpecialDeal() { return specialDeal != null && !specialDeal.isEmpty(); }
    }

    /**
     * Transaction data class
     */
    public static class TransactionData {
        private final String itemName;
        private final int quantity;
        private final double amount;
        private final TransactionType type;

        public TransactionData(String itemName, int quantity, double amount, TransactionType type) {
            this.itemName = itemName;
            this.quantity = quantity;
            this.amount = amount;
            this.type = type;
        }

        public String getItemName() { return itemName; }
        public int getQuantity() { return quantity; }
        public double getAmount() { return amount; }
        public TransactionType getType() { return type; }

        public enum TransactionType {
            BUY, SELL
        }
    }

    /**
     * Static factory method for easy plugin creation
     */
    public static ShopPlotPluginExample create() {
        return new ShopPlotPluginExample();
    }
}