package dev.mintychochip.guilds.models;

/**
 * Represents a location in the Minecraft world
 */
public class Location {

    /** The x. */
    private double x;
    /** The y. */
    private double y;
    /** The z. */
    private double z;
    /** The yaw. */
    private float yaw;
    /** The pitch. */
    private float pitch;
    /** The world. */
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
    /**
     * Returns the x.
     * @return the result
     */
    public double getX() {
        return x;
    }

    /**
     * Sets the x.
     * @param x the x
     */
    public void setX(double x) {
        this.x = x;
    }

    /**
     * Returns the y.
     * @return the result
     */
    public double getY() {
        return y;
    }

    /**
     * Sets the y.
     * @param y the y
     */
    public void setY(double y) {
        this.y = y;
    }

    /**
     * Returns the z.
     * @return the result
     */
    public double getZ() {
        return z;
    }

    /**
     * Sets the z.
     * @param z the z
     */
    public void setZ(double z) {
        this.z = z;
    }

    /**
     * Returns the yaw.
     * @return the result
     */
    public float getYaw() {
        return yaw;
    }

    /**
     * Sets the yaw.
     * @param yaw the yaw
     */
    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    /**
     * Returns the pitch.
     * @return the result
     */
    public float getPitch() {
        return pitch;
    }

    /**
     * Sets the pitch.
     * @param pitch the pitch
     */
    public void setPitch(float pitch) {
        this.pitch = pitch;
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

    /**
     * Indicates whether another object is equal to this one.
     * @param obj the obj
     */
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

    /** Returns a hash code for this object. */
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

    /** Returns a string representation of this object. */
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