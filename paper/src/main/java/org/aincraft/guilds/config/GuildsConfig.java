package org.aincraft.guilds.config;


import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration manager for Guilds plugin settings (reads {@code guilds-config.yml}).
 */
public class GuildsConfig {

    private final FileConfiguration config;


    public GuildsConfig(FileConfiguration config) {
        this.config = config;
        loadDefaults();
    }

    /**
     * Load default configuration values
     */
    private void loadDefaults() {
        // Guild settings
        config.addDefault("town.default_tax", 0.0);
        config.addDefault("town.max_name_length", 20);
        config.addDefault("town.min_name_length", 3);
        config.addDefault("town.max_residents", 50);
        config.addDefault("town.creation_cost", 0.0);
        config.addDefault("town.deletion_refund", 0.0);

        // Plot settings
        config.addDefault("plot.size", 16);
        config.addDefault("plot.claim_cost", 0.0);
        config.addDefault("plot.unclaim_refund", 0.0);

        // Permission settings
        config.addDefault("permissions.default_build", false);
        config.addDefault("permissions.default_destroy", false);
        config.addDefault("permissions.default_switch", false);
        config.addDefault("permissions.default_item_use", false);

        // Database settings
        config.addDefault("database.auto_backup", true);
        config.addDefault("database.backup_interval", 3600); // seconds

        // General settings
        config.addDefault("language", "en");
        config.addDefault("debug", false);
    }

    // Guild configuration methods
    public double getDefaultGuildTax() {
        return config.getDouble("town.default_tax", 0.0);
    }

    public int getMaxGuildNameLength() {
        return config.getInt("town.max_name_length", 20);
    }

    public int getMinGuildNameLength() {
        return config.getInt("town.min_name_length", 3);
    }

    public int getMaxGuildResidents() {
        return config.getInt("town.max_residents", 50);
    }

    public double getGuildCreationCost() {
        return config.getDouble("town.creation_cost", 0.0);
    }

    public double getGuildDeletionRefund() {
        return config.getDouble("town.deletion_refund", 0.0);
    }

    // Plot configuration methods
    public int getPlotSize() {
        return config.getInt("plot.size", 16);
    }

    public double getPlotClaimCost() {
        return config.getDouble("plot.claim_cost", 0.0);
    }

    public double getPlotUnclaimRefund() {
        return config.getDouble("plot.unclaim_refund", 0.0);
    }

    // Permission configuration methods
    public boolean getDefaultBuildPermission() {
        return config.getBoolean("permissions.default_build", false);
    }

    public boolean getDefaultDestroyPermission() {
        return config.getBoolean("permissions.default_destroy", false);
    }

    public boolean getDefaultSwitchPermission() {
        return config.getBoolean("permissions.default_switch", false);
    }

    public boolean getDefaultItemUsePermission() {
        return config.getBoolean("permissions.default_item_use", false);
    }

    // Database configuration methods
    public boolean isAutoBackupEnabled() {
        return config.getBoolean("database.auto_backup", true);
    }

    public int getBackupInterval() {
        return config.getInt("database.backup_interval", 3600);
    }

    // General configuration methods
    public String getLanguage() {
        return config.getString("language", "en");
    }

    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }
}
