package org.aincraft.towny.models;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a town block (plot) in the Towny system
 */
public class TownBlock {

    private UUID id;
    private int x;
    private int z;
    private String world;
    private String townId;
    private UUID ownerId;
    private String plotType;
    private String plotTypeDefinition; // Reference to PlotTypeDefinition
    private double price;
    private int permissionsFlags;
    private LocalDateTime claimedAt;
    private String customName;

    /**
     * Default constructor for database mapping
     */
    public TownBlock() {
        this.id = UUID.randomUUID();
        this.permissionsFlags = Permission.Flag.DEFAULT_PLOT;
        this.plotType = PlotTypes.DEFAULT;
        this.price = 0.0;
        this.claimedAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new town block
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @param townId Town ID
     */
    public TownBlock(int x, int z, String world, String townId) {
        this();
        this.x = x;
        this.z = z;
        this.world = world;
        this.townId = townId;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public String getTownId() {
        return townId;
    }

    public void setTownId(String townId) {
        this.townId = townId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getPlotType() {
        return plotType;
    }

    public void setPlotType(String plotType) {
        this.plotType = plotType != null ? plotType : PlotTypes.DEFAULT;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = Math.max(0.0, price); // Ensure non-negative
    }

    public int getPermissionsFlags() {
        return permissionsFlags;
    }

    public void setPermissionsFlags(int permissionsFlags) {
        this.permissionsFlags = permissionsFlags;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String getPlotTypeDefinition() {
        return plotTypeDefinition;
    }

    public void setPlotTypeDefinition(String plotTypeDefinition) {
        this.plotTypeDefinition = plotTypeDefinition;
    }

    // Business methods

    /**
     * Check if this plot has an owner (not town-owned)
     * @return True if has owner
     */
    public boolean hasOwner() {
        return ownerId != null;
    }

    /**
     * Check if this plot is town-owned
     * @return True if town-owned
     */
    public boolean isTownOwned() {
        return ownerId == null;
    }

    /**
     * Check if a specific UUID is the owner
     * @param uuid UUID to check
     * @return True if is owner
     */
    public boolean isOwner(UUID uuid) {
        return Objects.equals(ownerId, uuid);
    }

    /**
     * Check if this plot is for sale
     * @return True if for sale (has price > 0)
     */
    public boolean isForSale() {
        return price > 0.0;
    }

    /**
     * Set the plot for sale with a specific price
     * @param price Sale price
     */
    public void setForSale(double price) {
        this.price = Math.max(0.0, price);
    }

    /**
     * Remove plot from sale
     */
    public void removeFromSale() {
        this.price = 0.0;
    }

    /**
     * Get the chunk coordinates for this plot
     * @return Chunk coordinates [chunkX, chunkZ]
     */
    public int[] getChunkCoordinates() {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return new int[]{chunkX, chunkZ};
    }

    /**
     * Get the world coordinates for the center of this plot
     * @return Center coordinates [centerX, centerZ]
     */
    public int[] getCenterCoordinates() {
        int centerX = (x << 4) + 8;
        int centerZ = (z << 4) + 8;
        return new int[]{centerX, centerZ};
    }

    /**
     * Check if this plot has a specific permission flag
     * @param flag Permission flag to check
     * @return True if has permission
     */
    public boolean hasPermissionFlag(int flag) {
        return (permissionsFlags & flag) != 0;
    }

    /**
     * Add a permission flag to this plot
     * @param flag Permission flag to add
     */
    public void addPermissionFlag(int flag) {
        permissionsFlags |= flag;
    }

    /**
     * Remove a permission flag from this plot
     * @param flag Permission flag to remove
     */
    public void removePermissionFlag(int flag) {
        permissionsFlags &= ~flag;
    }

    /**
     * Set a specific permission flag for this plot
     * @param flag Permission flag to set
     * @param value Permission value (true to add, false to remove)
     */
    public void setPermissionFlag(int flag, boolean value) {
        if (value) {
            addPermissionFlag(flag);
        } else {
            removePermissionFlag(flag);
        }
    }

    /**
     * Get permission by name for backward compatibility
     * @param permissionName Permission name (build, destroy, switch, item_use)
     * @return True if has permission
     */
    public boolean hasPermission(String permissionName) {
        switch (permissionName.toLowerCase()) {
            case "build":
                return hasPermissionFlag(Permission.Flag.BUILD);
            case "destroy":
                return hasPermissionFlag(Permission.Flag.DESTROY);
            case "switch":
                return hasPermissionFlag(Permission.Flag.SWITCH);
            case "item_use":
                return hasPermissionFlag(Permission.Flag.ITEM_USE);
            default:
                return false;
        }
    }

    /**
     * Set permission by name for backward compatibility
     * @param permissionName Permission name (build, destroy, switch, item_use)
     * @param value Permission value
     */
    public void setPermission(String permissionName, boolean value) {
        switch (permissionName.toLowerCase()) {
            case "build":
                setPermissionFlag(Permission.Flag.BUILD, value);
                break;
            case "destroy":
                setPermissionFlag(Permission.Flag.DESTROY, value);
                break;
            case "switch":
                setPermissionFlag(Permission.Flag.SWITCH, value);
                break;
            case "item_use":
                setPermissionFlag(Permission.Flag.ITEM_USE, value);
                break;
        }
    }

    /**
     * Get a list of all active permission flags for this plot
     * @return List of active permission names
     */
    public List<String> getActivePermissionNames() {
        List<String> activePermissions = new ArrayList<>();

        if (hasPermissionFlag(Permission.Flag.BUILD)) activePermissions.add("BUILD");
        if (hasPermissionFlag(Permission.Flag.DESTROY)) activePermissions.add("DESTROY");
        if (hasPermissionFlag(Permission.Flag.SWITCH)) activePermissions.add("SWITCH");
        if (hasPermissionFlag(Permission.Flag.ITEM_USE)) activePermissions.add("ITEM_USE");
        if (hasPermissionFlag(Permission.Flag.PLOT_PERM)) activePermissions.add("PLOT_PERM");
        if (hasPermissionFlag(Permission.Flag.PLOT_SET)) activePermissions.add("PLOT_SET");
        if (hasPermissionFlag(Permission.Flag.PLOT_OWNER)) activePermissions.add("PLOT_OWNER");

        return activePermissions;
    }

    /**
     * Reset permissions to default for this plot type and ownership
     */
    public void resetToDefaultPermissions() {
        if (hasOwner()) {
            // Player-owned plots get full permissions for owner
            permissionsFlags = Permission.Flag.ALL;
        } else {
            // Town-owned plots get default permissions based on type
            permissionsFlags = PlotTypes.getDefaultPermissions(plotType);
        }
    }

    /**
     * Check if the plot allows build actions for non-owners
     * @return True if non-owners can build
     */
    public boolean allowsPublicBuild() {
        return hasPermissionFlag(Permission.Flag.BUILD);
    }

    /**
     * Check if the plot allows destroy actions for non-owners
     * @return True if non-owners can destroy
     */
    public boolean allowsPublicDestroy() {
        return hasPermissionFlag(Permission.Flag.DESTROY);
    }

    /**
     * Get plot type display name
     * @return Formatted plot type name
     */
    public String getPlotTypeDisplayName() {
        if (customName != null && !customName.trim().isEmpty()) {
            return customName;
        }
        return PlotTypes.getDisplayName(plotType);
    }

    /**
     * Check if this plot has an extensible plot type definition
     * @return True if plot type definition is set
     */
    public boolean hasPlotTypeDefinition() {
        return plotTypeDefinition != null && !plotTypeDefinition.trim().isEmpty();
    }

    /**
     * Check if this plot type supports custom metadata
     * @return True if plot type has extensible definition
     */
    public boolean supportsCustomMetadata() {
        return hasPlotTypeDefinition();
    }

    /**
     * Get the effective plot type name
     * @return Effective plot type name
     */
    public String getEffectivePlotType() {
        if (hasPlotTypeDefinition()) {
            return plotTypeDefinition;
        }
        return plotType != null ? plotType : PlotTypes.DEFAULT;
    }

    /**
     * Check if this plot type is a built-in type
     * @return True if built-in plot type
     */
    public boolean isBuiltInPlotType() {
        return PlotTypes.isBuiltIn(getEffectivePlotType());
    }

    /**
     * Get plot type category for grouping
     * @return Plot type category
     */
    public String getPlotTypeCategory() {
        return PlotTypes.getCategory(getEffectivePlotType());
    }

    /**
     * Check if this plot type supports a specific feature
     * @param feature Feature name to check
     * @return True if feature is supported
     */
    public boolean supportsFeature(String feature) {
        return PlotTypes.supportsFeature(getEffectivePlotType(), feature);
    }

    /**
     * Get plot priority for ordering and display purposes
     * @return Priority value (higher = more important)
     */
    public int getPlotTypePriority() {
        return PlotTypes.getPriority(getEffectivePlotType());
    }

    /**
     * Check if this plot is adjacent to another plot
     * @param other Other plot
     * @return True if adjacent
     */
    public boolean isAdjacentTo(TownBlock other) {
        if (!this.world.equals(other.world)) {
            return false;
        }

        int dx = Math.abs(this.x - other.x);
        int dz = Math.abs(this.z - other.z);

        // Adjacent if exactly 1 block away in either direction (not diagonal)
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }

    /**
     * Get all neighboring plots (would need service to find them)
     * @return Array of neighboring plot coordinates
     */
    public int[][] getNeighborCoordinates() {
        return new int[][]{
                {x - 1, z}, // West
                {x + 1, z}, // East
                {x, z - 1}, // North
                {x, z + 1}  // South
        };
    }

    /**
     * Calculate distance to another plot
     * @param other Other plot
     * @return Distance in plot units
     */
    public double distanceTo(TownBlock other) {
        if (!this.world.equals(other.world)) {
            return Double.MAX_VALUE;
        }

        int dx = this.x - other.x;
        int dz = this.z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TownBlock townBlock = (TownBlock) obj;
        return id.equals(townBlock.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "TownBlock{" +
                "id=" + id +
                ", x=" + x +
                ", z=" + z +
                ", world='" + world + '\'' +
                ", townId='" + townId + '\'' +
                ", plotType='" + plotType + '\'' +
                ", hasOwner=" + hasOwner() +
                ", price=" + price +
                '}';
    }
}