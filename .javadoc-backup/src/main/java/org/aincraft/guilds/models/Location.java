package org.aincraft.guilds.models;

/**
 * Represents a location in the Minecraft world
 */
public class Location {

    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String world;

    /**
     * Default constructor
     */
    public Location() {
        this.x = 0.0;
        this.y = 64.0; // Default ground level
        this.z = 0.0;
        this.yaw = 0.0f;
        this.pitch = 0.0f;
        this.world = "world";
    }

    /**
     * Constructor with coordinates
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param world World name
     */
    public Location(double x, double y, double z, String world) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = 0.0f;
        this.pitch = 0.0f;
        this.world = world;
    }

    /**
     * Constructor with full location data
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param yaw Yaw rotation
     * @param pitch Pitch rotation
     * @param world World name
     */
    public Location(double x, double y, double z, float yaw, float pitch, String world) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.world = world;
    }

    // Getters and Setters
    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    // Utility methods

    /**
     * Get the block coordinates (integer values)
     * @return Block coordinates [blockX, blockY, blockZ]
     */
    public int[] getBlockCoordinates() {
        return new int[]{
            (int) Math.floor(x),
            (int) Math.floor(y),
            (int) Math.floor(z)
        };
    }

    /**
     * Get the chunk coordinates
     * @return Chunk coordinates [chunkX, chunkZ]
     */
    public int[] getChunkCoordinates() {
        return new int[]{
            (int) Math.floor(x) >> 4,
            (int) Math.floor(z) >> 4
        };
    }

    /**
     * Calculate distance to another location (same world only)
     * @param other Other location
     * @return Distance, or Double.MAX_VALUE if different worlds
     */
    public double distanceTo(Location other) {
        if (!this.world.equals(other.world)) {
            return Double.MAX_VALUE;
        }

        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Calculate squared distance to another location (faster comparison)
     * @param other Other location
     * @return Squared distance, or Double.MAX_VALUE if different worlds
     */
    public double distanceSquaredTo(Location other) {
        if (!this.world.equals(other.world)) {
            return Double.MAX_VALUE;
        }

        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Check if this location is within a certain radius of another location
     * @param other Other location
     * @param radius Radius to check
     * @return True if within radius
     */
    public boolean isWithinRadius(Location other, double radius) {
        return distanceSquaredTo(other) <= radius * radius;
    }

    /**
     * Create a copy of this location
     * @return New location with same values
     */
    public Location clone() {
        return new Location(x, y, z, yaw, pitch, world);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Location location = (Location) obj;
        return Double.compare(location.x, x) == 0 &&
               Double.compare(location.y, y) == 0 &&
               Double.compare(location.z, z) == 0 &&
               Float.compare(location.yaw, yaw) == 0 &&
               Float.compare(location.pitch, pitch) == 0 &&
               world.equals(location.world);
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        result = 31 * result + Double.hashCode(z);
        result = 31 * result + Float.hashCode(yaw);
        result = 31 * result + Float.hashCode(pitch);
        result = 31 * result + world.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("Location{x=%.2f, y=%.2f, z=%.2f, yaw=%.1f, pitch=%.1f, world='%s'}",
                           x, y, z, yaw, pitch, world);
    }

    /**
     * Format location for display purposes
     * @return Formatted string
     */
    public String toDisplayString() {
        return String.format("%.1f, %.1f, %.1f in %s", x, y, z, world);
    }
}