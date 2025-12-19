package org.aincraft.subregion;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Represents a freeform cuboid subregion within guild-claimed chunks.
 * Subregions allow fine-grained permission control over specific areas.
 */
public final class Subregion {
    private final UUID id;
    private final UUID guildId;
    private String name;
    private final String world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final UUID createdBy;
    private final long createdAt;
    private final Set<UUID> owners;
    private int permissions;
    private String type;  // Type ID (nullable for untyped regions)

    /**
     * Creates a new Subregion without a type.
     */
    public Subregion(UUID guildId, String name, String world,
                     int minX, int minY, int minZ,
                     int maxX, int maxY, int maxZ,
                     UUID createdBy) {
        this(guildId, name, world, minX, minY, minZ, maxX, maxY, maxZ, createdBy, null);
    }

    /**
     * Creates a new Subregion with an optional type.
     */
    public Subregion(UUID guildId, String name, String world,
                     int minX, int minY, int minZ,
                     int maxX, int maxY, int maxZ,
                     UUID createdBy, String type) {
        this.id = UUID.randomUUID();
        this.guildId = Objects.requireNonNull(guildId, "Guild ID cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.world = Objects.requireNonNull(world, "World cannot be null");
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.createdBy = Objects.requireNonNull(createdBy, "Creator cannot be null");
        this.createdAt = System.currentTimeMillis();
        this.owners = new HashSet<>();
        this.owners.add(createdBy);
        this.permissions = 0;
        this.type = type;
    }

    /**
     * Creates a Subregion with existing data (for database restoration).
     */
    public Subregion(UUID id, UUID guildId, String name, String world,
                     int minX, int minY, int minZ,
                     int maxX, int maxY, int maxZ,
                     UUID createdBy, long createdAt,
                     Set<UUID> owners, int permissions, String type) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.guildId = Objects.requireNonNull(guildId, "Guild ID cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.world = Objects.requireNonNull(world, "World cannot be null");
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.createdBy = Objects.requireNonNull(createdBy, "Creator cannot be null");
        this.createdAt = createdAt;
        this.owners = new HashSet<>(owners);
        this.permissions = permissions;
        this.type = type;
    }

    /**
     * Creates a Subregion from two Bukkit Locations without a type.
     */
    public static Subregion fromLocations(UUID guildId, String name, Location pos1, Location pos2, UUID createdBy) {
        return fromLocations(guildId, name, pos1, pos2, createdBy, null);
    }

    /**
     * Creates a Subregion from two Bukkit Locations with an optional type.
     */
    public static Subregion fromLocations(UUID guildId, String name, Location pos1, Location pos2, UUID createdBy, String type) {
        Objects.requireNonNull(pos1, "Position 1 cannot be null");
        Objects.requireNonNull(pos2, "Position 2 cannot be null");

        if (!pos1.getWorld().getName().equals(pos2.getWorld().getName())) {
            throw new IllegalArgumentException("Positions must be in the same world");
        }

        return new Subregion(
                guildId, name, pos1.getWorld().getName(),
                pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ(),
                createdBy, type
        );
    }

    /**
     * Checks if a location is within this subregion.
     */
    public boolean contains(Location loc) {
        if (loc == null || !loc.getWorld().getName().equals(world)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }

    /**
     * Checks if this subregion intersects with a chunk.
     */
    public boolean intersectsChunk(String chunkWorld, int chunkX, int chunkZ) {
        if (!world.equals(chunkWorld)) {
            return false;
        }
        int chunkMinX = chunkX * 16;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunkZ * 16;
        int chunkMaxZ = chunkMinZ + 15;

        return minX <= chunkMaxX && maxX >= chunkMinX &&
               minZ <= chunkMaxZ && maxZ >= chunkMinZ;
    }

    /**
     * Calculates the volume of this subregion in blocks.
     */
    public long getVolume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /**
     * Gets all chunk coordinates that this subregion intersects.
     */
    public Set<long[]> getIntersectingChunks() {
        Set<long[]> chunks = new HashSet<>();
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunks.add(new long[]{cx, cz});
            }
        }
        return chunks;
    }

    public boolean isOwner(UUID playerId) {
        return owners.contains(playerId);
    }

    public boolean addOwner(UUID playerId) {
        return owners.add(Objects.requireNonNull(playerId));
    }

    public boolean removeOwner(UUID playerId) {
        if (playerId.equals(createdBy)) {
            return false;
        }
        return owners.remove(playerId);
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getGuildId() { return guildId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = Objects.requireNonNull(name); }
    public String getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
    public UUID getCreatedBy() { return createdBy; }
    public long getCreatedAt() { return createdAt; }
    public Set<UUID> getOwners() { return Collections.unmodifiableSet(owners); }
    public int getPermissions() { return permissions; }
    public void setPermissions(int permissions) { this.permissions = permissions; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Subregion)) return false;
        Subregion subregion = (Subregion) o;
        return Objects.equals(id, subregion.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Subregion{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", world='" + world + '\'' +
                ", bounds=[" + minX + "," + minY + "," + minZ + " to " + maxX + "," + maxY + "," + maxZ + "]" +
                '}';
    }
}
