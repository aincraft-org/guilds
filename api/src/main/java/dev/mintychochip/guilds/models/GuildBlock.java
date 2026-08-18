package dev.mintychochip.guilds.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a guild block (plot) in the Guilds system
 */
public class GuildBlock {

    /** The id. */
    private UUID id;
    /** The x. */
    private int x;
    /** The z. */
    private int z;
    /** The world. */
    private String world;
    /** The guild id. */
    private String guildId;
    /** The owner id. */
    private UUID ownerId;
    /** The plot type. */
    private String plotType;
    /** The plot type definition. */
    private String plotTypeDefinition; // Reference to PlotTypeDefinition
    /** The price. */
    private double price;
    /** The permissions flags. */
    private int permissionsFlags;
    /** The claimed at. */
    private LocalDateTime claimedAt;
    /** The custom name. */
    private String customName;

    /**
     * Default constructor for database mapping
     */
    public GuildBlock() {
        this.id = UUID.randomUUID();
        this.permissionsFlags = PermissionSet.createDefaultPlot().toLegacyFlags();
        this.plotType = PlotTypes.DEFAULT;
        this.price = 0.0;
        this.claimedAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new guild block
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @param guildId Guild ID
     */
    public GuildBlock(int x, int z, String world, String guildId) {
        this();
        this.x = x;
        this.z = z;
        this.world = world;
        this.guildId = guildId;
    }

    // Getters and Setters
    /**
     * Returns the id.
     * @return the result
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the id.
     * @param id the id
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the x.
     * @return the result
     */
    public int getX() {
        return x;
    }

    /**
     * Sets the x.
     * @param x the x
     */
    public void setX(int x) {
        this.x = x;
    }

    /**
     * Returns the z.
     * @return the result
     */
    public int getZ() {
        return z;
    }

    /**
     * Sets the z.
     * @param z the z
     */
    public void setZ(int z) {
        this.z = z;
    }

    /**
     * Returns the world.
     * @return the result
     */
    public String getWorld() {
        return world;
    }

    /**
     * Sets the world.
     * @param world the world
     */
    public void setWorld(String world) {
        this.world = world;
    }

    /**
     * Returns the guild id.
     * @return the result
     */
    public String getGuildId() {
        return guildId;
    }

    /**
     * Sets the guild id.
     * @param guildId the guild id
     */
    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    /**
     * Returns the owner id.
     * @return the result
     */
    public UUID getOwnerId() {
        return ownerId;
    }

    /**
     * Sets the owner id.
     * @param ownerId the owner id
     */
    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    /**
     * Returns the plot type.
     * @return the result
     */
    public String getPlotType() {
        return plotType;
    }

    /**
     * Sets the plot type.
     * @param plotType the plot type
     */
    public void setPlotType(String plotType) {
        this.plotType = plotType != null ? plotType : PlotTypes.DEFAULT;
    }

    /**
     * Returns the price.
     * @return the result
     */
    public double getPrice() {
        return price;
    }

    /**
     * Sets the price.
     * @param price the price
     */
    public void setPrice(double price) {
        this.price = Math.max(0.0, price); // Ensure non-negative
    }

    /**
     * Returns the permissions flags.
     * @return the result
     */
    public int getPermissionsFlags() {
        return permissionsFlags;
    }

    /**
     * Sets the permissions flags.
     * @param permissionsFlags the permissions flags
     */
    public void setPermissionsFlags(int permissionsFlags) {
        this.permissionsFlags = permissionsFlags;
    }

    /**
     * Returns the claimed at.
     * @return the result
     */
    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    /**
     * Sets the claimed at.
     * @param claimedAt the claimed at
     */
    public void setClaimedAt(LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    /**
     * Returns the custom name.
     * @return the result
     */
    public String getCustomName() {
        return customName;
    }

    /**
     * Sets the custom name.
     * @param customName the custom name
     */
    public void setCustomName(String customName) {
        this.customName = customName;
    }

    /**
     * Returns the plot type definition.
     * @return the result
     */
    public String getPlotTypeDefinition() {
        return plotTypeDefinition;
    }

    /**
     * Sets the plot type definition.
     * @param plotTypeDefinition the plot type definition
     */
    public void setPlotTypeDefinition(String plotTypeDefinition) {
        this.plotTypeDefinition = plotTypeDefinition;
    }

    // Business methods

    /**
     * Check if this plot has an owner (not guild-owned)
     * @return True if has owner
     */
    public boolean hasOwner() {
        return ownerId != null;
    }

    /**
     * Check if this plot is guild-owned
     * @return True if guild-owned
     */
    public boolean isGuildOwned() {
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
                return hasPermissionFlag(GuildPermission.BUILD.getLegacyBitwiseValue());
            case "destroy":
                return hasPermissionFlag(GuildPermission.DESTROY.getLegacyBitwiseValue());
            case "switch":
                return hasPermissionFlag(GuildPermission.SWITCH.getLegacyBitwiseValue());
            case "item_use":
                return hasPermissionFlag(GuildPermission.ITEM_USE.getLegacyBitwiseValue());
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
                setPermissionFlag(GuildPermission.BUILD.getLegacyBitwiseValue(), value);
                break;
            case "destroy":
                setPermissionFlag(GuildPermission.DESTROY.getLegacyBitwiseValue(), value);
                break;
            case "switch":
                setPermissionFlag(GuildPermission.SWITCH.getLegacyBitwiseValue(), value);
                break;
            case "item_use":
                setPermissionFlag(GuildPermission.ITEM_USE.getLegacyBitwiseValue(), value);
                break;
        }
    }

    /**
     * Get a list of all active permission flags for this plot
     * @return List of active permission names
     */
    public List<String> getActivePermissionNames() {
        List<String> activePermissions = new ArrayList<>();

        if (hasPermissionFlag(GuildPermission.BUILD.getLegacyBitwiseValue())) activePermissions.add("BUILD");
        if (hasPermissionFlag(GuildPermission.DESTROY.getLegacyBitwiseValue())) activePermissions.add("DESTROY");
        if (hasPermissionFlag(GuildPermission.SWITCH.getLegacyBitwiseValue())) activePermissions.add("SWITCH");
        if (hasPermissionFlag(GuildPermission.ITEM_USE.getLegacyBitwiseValue())) activePermissions.add("ITEM_USE");
        if (hasPermissionFlag(GuildPermission.PLOT_PERM.getLegacyBitwiseValue())) activePermissions.add("PLOT_PERM");
        if (hasPermissionFlag(GuildPermission.PLOT_SET.getLegacyBitwiseValue())) activePermissions.add("PLOT_SET");
        if (hasPermissionFlag(GuildPermission.PLOT_OWNER.getLegacyBitwiseValue())) activePermissions.add("PLOT_OWNER");

        return activePermissions;
    }

    /**
     * Reset permissions to default for this plot type and ownership
     */
    public void resetToDefaultPermissions() {
        if (hasOwner()) {
            // Player-owned plots get full permissions for owner
            permissionsFlags = GuildPermission.ALL;
        } else {
            // Guild-owned plots get default permissions based on type
            permissionsFlags = PlotTypes.getDefaultPermissions(plotType);
        }
    }

    /**
     * Check if the plot allows build actions for non-owners
     * @return True if non-owners can build
     */
    public boolean allowsPublicBuild() {
        return hasPermissionFlag(GuildPermission.BUILD.getLegacyBitwiseValue());
    }

    /**
     * Check if the plot allows destroy actions for non-owners
     * @return True if non-owners can destroy
     */
    public boolean allowsPublicDestroy() {
        return hasPermissionFlag(GuildPermission.DESTROY.getLegacyBitwiseValue());
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
    public boolean isAdjacentTo(GuildBlock other) {
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
    public double distanceTo(GuildBlock other) {
        if (!this.world.equals(other.world)) {
            return Double.MAX_VALUE;
        }

        int dx = this.x - other.x;
        int dz = this.z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Indicates whether another object is equal to this one.
     * @param obj the obj
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GuildBlock guildBlock = (GuildBlock) obj;
        return id.equals(guildBlock.id);
    }

    /** Returns a hash code for this object. */
    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Returns a string representation of this object. */
    @Override
    public String toString() {
        return "GuildBlock{" +
                "id=" + id +
                ", x=" + x +
                ", z=" + z +
                ", world='" + world + '\'' +
                ", guildId='" + guildId + '\'' +
                ", plotType='" + plotType + '\'' +
                ", hasOwner=" + hasOwner() +
                ", price=" + price +
                '}';
    }
}