package dev.mintychochip.guilds.config;


import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration manager for Guilds plugin settings (reads {@code guilds-config.yml}).
 */
public class GuildsConfig {

    /** The config. */
    private final FileConfiguration config;


    /**
     * Creates a new guilds config instance.
     * @param config the config
     */
    public GuildsConfig(FileConfiguration config) {
        this.config = config;
        loadDefaults();
    }

    /**
     * Load default configuration values
     */
    private void loadDefaults() {
        // Guild settings
        config.addDefault("guild.default_tax", 0.0);
        config.addDefault("guild.max_name_length", 20);
        config.addDefault("guild.min_name_length", 3);
        config.addDefault("guild.max_residents", 50);
        config.addDefault("guild.creation_cost", 0.0);
        config.addDefault("guild.deletion_refund", 0.0);

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
    /**
     * Returns the default guild tax.
     * @return the result
     */
    public double getDefaultGuildTax() {
        return config.getDouble("guild.default_tax", 0.0);
    }

    /**
     * Returns the max guild name length.
     * @return the result
     */
    public int getMaxGuildNameLength() {
        return config.getInt("guild.max_name_length", 20);
    }

    /**
     * Returns the min guild name length.
     * @return the result
     */
    public int getMinGuildNameLength() {
        return config.getInt("guild.min_name_length", 3);
    }

    /**
     * Returns the max guild residents.
     * @return the result
     */
    public int getMaxGuildResidents() {
        return config.getInt("guild.max_residents", 50);
    }

    /**
     * Returns the guild creation cost.
     * @return the result
     */
    public double getGuildCreationCost() {
        return config.getDouble("guild.creation_cost", 0.0);
    }

    /**
     * Returns the guild deletion refund.
     * @return the result
     */
    public double getGuildDeletionRefund() {
        return config.getDouble("guild.deletion_refund", 0.0);
    }

    // Plot configuration methods
    /**
     * Returns the plot size.
     * @return the result
     */
    public int getPlotSize() {
        return config.getInt("plot.size", 16);
    }

    /**
     * Returns the plot claim cost.
     * @return the result
     */
    public double getPlotClaimCost() {
        return config.getDouble("plot.claim_cost", 0.0);
    }

    /**
     * Returns the plot unclaim refund.
     * @return the result
     */
    public double getPlotUnclaimRefund() {
        return config.getDouble("plot.unclaim_refund", 0.0);
    }

    // Permission configuration methods
    /**
     * Returns the default build permission.
     * @return the result
     */
    public boolean getDefaultBuildPermission() {
        return config.getBoolean("permissions.default_build", false);
    }

    /**
     * Returns the default destroy permission.
     * @return the result
     */
    public boolean getDefaultDestroyPermission() {
        return config.getBoolean("permissions.default_destroy", false);
    }

    /**
     * Returns the default switch permission.
     * @return the result
     */
    public boolean getDefaultSwitchPermission() {
        return config.getBoolean("permissions.default_switch", false);
    }

    /**
     * Returns the default item use permission.
     * @return the result
     */
    public boolean getDefaultItemUsePermission() {
        return config.getBoolean("permissions.default_item_use", false);
    }

    // Database configuration methods
    /**
     * Returns whether auto backup enabled.
     * @return the result
     */
    public boolean isAutoBackupEnabled() {
        return config.getBoolean("database.auto_backup", true);
    }

    /**
     * Returns the backup interval.
     * @return the result
     */
    public int getBackupInterval() {
        return config.getInt("database.backup_interval", 3600);
    }

    // General configuration methods
    /**
     * Returns the language.
     * @return the result
     */
    public String getLanguage() {
        return config.getString("language", "en");
    }

    /**
     * Returns whether debug enabled.
     * @return the result
     */
    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }
}
