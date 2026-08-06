package org.aincraft.towny.models;

/**
 * Utility class for plot type constants and categorization logic
 * Extracted from TownBlock.java to improve readability and maintainability
 */

public class PlotTypes {

    // Built-in plot type constants
    public static final String DEFAULT = "default";
    public static final String SHOP = "shop";
    public static final String FARM = "farm";
    public static final String WILDERNESS = "wilderness";
    public static final String BANK = "bank";
    public static final String INN = "inn";
    public static final String EMBASSY = "embassy";
    public static final String JAIL = "jail";
    public static final String ARENA = "arena";

    // Private constructor to prevent instantiation
    private PlotTypes() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Check if a plot type is a built-in type
     * @param plotType Plot type to check
     * @return True if built-in
     */
    public static boolean isBuiltIn(String plotType) {
        if (plotType == null) return false;

        switch (plotType) {
            case DEFAULT:
            case SHOP:
            case FARM:
            case WILDERNESS:
            case BANK:
            case INN:
            case EMBASSY:
            case JAIL:
            case ARENA:
                return true;
            default:
                return false;
        }
    }

    /**
     * Get the category for a plot type
     * @param plotType Plot type
     * @return Category name
     */
    public static String getCategory(String plotType) {
        if (plotType == null) return "unknown";

        switch (plotType) {
            case SHOP:
            case BANK:
                return "commercial";
            case FARM:
                return "agricultural";
            case INN:
            case EMBASSY:
                return "service";
            case JAIL:
            case ARENA:
                return "special";
            case WILDERNESS:
                return "wilderness";
            case DEFAULT:
            default:
                return isBuiltIn(plotType) ? "residential" : "custom";
        }
    }

    /**
     * Get the priority for a plot type (higher = more important)
     * @param plotType Plot type
     * @return Priority value
     */
    public static int getPriority(String plotType) {
        if (plotType == null) return 0;

        switch (plotType) {
            case ARENA:
            case JAIL:
                return 100; // High priority special areas
            case BANK:
            case SHOP:
                return 80; // Commercial areas
            case INN:
            case EMBASSY:
                return 60; // Service areas
            case FARM:
                return 40; // Agricultural
            case DEFAULT:
                return 20; // Residential
            case WILDERNESS:
                return 10; // Lowest priority
            default:
                return isBuiltIn(plotType) ? 0 : 50; // Custom types get medium priority
        }
    }

    /**
     * Check if a plot type supports a specific feature
     * @param plotType Plot type
     * @param feature Feature to check
     * @return True if feature is supported
     */
    public static boolean supportsFeature(String plotType, String feature) {
        if (plotType == null || feature == null) return false;

        if (!isBuiltIn(plotType)) {
            // For custom plot types, assume they support all features
            return true;
        }

        switch (plotType) {
            case SHOP:
                return "commerce".equals(feature) || "trade".equals(feature);
            case FARM:
                return "agriculture".equals(feature) || "growth".equals(feature);
            case ARENA:
                return "pvp".equals(feature) || "combat".equals(feature);
            case BANK:
                return "banking".equals(feature) || "economy".equals(feature);
            default:
                return false;
        }
    }

    /**
     * Get a formatted display name for a plot type
     * @param plotType Plot type string
     * @return Formatted display name
     */
    public static String getDisplayName(String plotType) {
        if (plotType == null || plotType.isEmpty()) {
            return "Unknown";
        }

        // Capitalize first letter
        return plotType.substring(0, 1).toUpperCase() + plotType.substring(1).toLowerCase();
    }

    /**
     * Get the default permission flags for a plot type
     * @param plotType Plot type
     * @return Default permission flags
     */
    public static int getDefaultPermissions(String plotType) {
        if (plotType == null) {
            return PermissionSet.createDefaultPlot().toLegacyFlags();
        }

        switch (plotType) {
            case SHOP:
                return PermissionSet.createDefaultPlot().toLegacyFlags();
            case FARM:
                return TownyPermission.BUILD_ALL;
            case DEFAULT:
            default:
                return PermissionSet.createDefaultPlot().toLegacyFlags();
        }
    }

    /**
     * Check if a plot type is commercial
     * @param plotType Plot type
     * @return True if commercial
     */
    public static boolean isCommercial(String plotType) {
        return "commercial".equals(getCategory(plotType));
    }

    /**
     * Check if a plot type is residential
     * @param plotType Plot type
     * @return True if residential
     */
    public static boolean isResidential(String plotType) {
        return "residential".equals(getCategory(plotType));
    }

    /**
     * Check if a plot type is a special type
     * @param plotType Plot type
     * @return True if special
     */
    public static boolean isSpecial(String plotType) {
        return "special".equals(getCategory(plotType));
    }
}
