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
    private double price;
    private Map<String, Boolean> permissions;
    private LocalDateTime claimedAt;
    private String customName;

    /**
     * Plot types available in the system
     */
    public static class PlotType {
        public static final String DEFAULT = "default";
        public static final String SHOP = "shop";
        public static final String FARM = "farm";
        public static final String WILDERNESS = "wilderness";
        public static final String BANK = "bank";
        public static final String INN = "inn";
        public static final String EMBASSY = "embassy";
        public static final String JAIL = "jail";
        public static final String ARENA = "arena";
    }

    /**
     * Default constructor for database mapping
     */
    public TownBlock() {
        this.id = UUID.randomUUID();
        this.permissions = new HashMap<>();
        this.plotType = PlotType.DEFAULT;
        this.price = 0.0;
        this.claimedAt = LocalDateTime.now();
        setDefaultPermissions();
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
        this.plotType = plotType != null ? plotType : PlotType.DEFAULT;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = Math.max(0.0, price); // Ensure non-negative
    }

    public Map<String, Boolean> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, Boolean> permissions) {
        this.permissions = permissions != null ? permissions : new HashMap<>();
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
     * Check if this plot has a specific permission
     * @param permission Permission node
     * @return True if has permission
     */
    public boolean hasPermission(String permission) {
        return permissions.getOrDefault(permission, false);
    }

    /**
     * Set a permission for this plot
     * @param permission Permission node
     * @param value Permission value
     */
    public void setPermission(String permission, boolean value) {
        permissions.put(permission, value);
    }

    /**
     * Set default permissions for a new plot
     */
    private void setDefaultPermissions() {
        permissions.put("build", false);
        permissions.put("destroy", false);
        permissions.put("switch", false);
        permissions.put("item_use", false);
    }

    /**
     * Get plot type display name
     * @return Formatted plot type name
     */
    public String getPlotTypeDisplayName() {
        if (customName != null && !customName.trim().isEmpty()) {
            return customName;
        }
        return plotType.substring(0, 1).toUpperCase() + plotType.substring(1).toLowerCase();
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