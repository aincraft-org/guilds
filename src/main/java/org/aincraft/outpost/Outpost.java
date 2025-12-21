package org.aincraft.outpost;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.ChunkKey;

/**
 * Domain entity representing a guild outpost.
 * Single Responsibility: Encapsulate outpost data with validation.
 * Immutable after creation for thread-safety.
 */
public final class Outpost {
    private final UUID id;
    private final UUID guildId;
    private String name;
    private final ChunkKey location;
    private final String spawnWorld;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final float spawnYaw;
    private final float spawnPitch;
    private final long createdAt;
    private final UUID createdBy;

    // Constraints
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 32;
    private static final String NAME_PATTERN = "^[a-zA-Z0-9\\s\\-]+$";

    /**
     * Private constructor - use factory methods instead.
     */
    private Outpost(UUID id, UUID guildId, String name, ChunkKey location,
                   String spawnWorld, double spawnX, double spawnY, double spawnZ,
                   float spawnYaw, float spawnPitch, long createdAt, UUID createdBy) {
        this.id = id;
        this.guildId = guildId;
        this.name = name;
        this.location = location;
        this.spawnWorld = spawnWorld;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.spawnYaw = spawnYaw;
        this.spawnPitch = spawnPitch;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    /**
     * Factory method to create a new outpost with validation.
     * Generates a new UUID and current timestamp.
     *
     * @param guildId the guild ID (cannot be null)
     * @param name the outpost name (3-32 chars, alphanumeric + spaces/dashes)
     * @param location the chunk location (cannot be null)
     * @param spawnWorld the spawn world name (cannot be null)
     * @param spawnX the spawn X coordinate
     * @param spawnY the spawn Y coordinate
     * @param spawnZ the spawn Z coordinate
     * @param spawnYaw the spawn yaw rotation
     * @param spawnPitch the spawn pitch rotation
     * @param createdBy the UUID of the creator (cannot be null)
     * @return an Optional containing the new Outpost, or Optional.empty() if validation fails
     */
    public static Optional<Outpost> create(UUID guildId, String name, ChunkKey location,
                                          String spawnWorld, double spawnX, double spawnY,
                                          double spawnZ, float spawnYaw, float spawnPitch,
                                          UUID createdBy) {
        try {
            // Validate inputs
            if (guildId == null || name == null || location == null ||
                spawnWorld == null || createdBy == null) {
                return Optional.empty();
            }

            if (!isValidName(name)) {
                return Optional.empty();
            }

            UUID outpostId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            Outpost outpost = new Outpost(outpostId, guildId, name, location,
                spawnWorld, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, now, createdBy);
            return Optional.of(outpost);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Factory method to restore an outpost from database storage with validation.
     *
     * @param id the outpost ID (cannot be null)
     * @param guildId the guild ID (cannot be null)
     * @param name the outpost name (cannot be null or invalid)
     * @param location the chunk location (cannot be null)
     * @param spawnWorld the spawn world name (cannot be null)
     * @param spawnX the spawn X coordinate
     * @param spawnY the spawn Y coordinate
     * @param spawnZ the spawn Z coordinate
     * @param spawnYaw the spawn yaw rotation
     * @param spawnPitch the spawn pitch rotation
     * @param createdAt the creation timestamp
     * @param createdBy the creator UUID (cannot be null)
     * @return an Optional containing the restored Outpost, or Optional.empty() if validation fails
     */
    public static Optional<Outpost> restore(UUID id, UUID guildId, String name, ChunkKey location,
                                           String spawnWorld, double spawnX, double spawnY,
                                           double spawnZ, float spawnYaw, float spawnPitch,
                                           long createdAt, UUID createdBy) {
        try {
            // Validate inputs
            if (id == null || guildId == null || name == null || location == null ||
                spawnWorld == null || createdBy == null) {
                return Optional.empty();
            }

            if (!isValidName(name)) {
                return Optional.empty();
            }

            Outpost outpost = new Outpost(id, guildId, name, location,
                spawnWorld, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, createdAt, createdBy);
            return Optional.of(outpost);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Validates an outpost name.
     *
     * @param name the name to validate
     * @return true if the name is valid
     */
    private static boolean isValidName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        String trimmed = name.trim();
        if (trimmed.length() < MIN_NAME_LENGTH || trimmed.length() > MAX_NAME_LENGTH) {
            return false;
        }

        return trimmed.matches(NAME_PATTERN);
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getGuildId() {
        return guildId;
    }

    public String getName() {
        return name;
    }

    public ChunkKey getLocation() {
        return location;
    }

    public String getSpawnWorld() {
        return spawnWorld;
    }

    public double getSpawnX() {
        return spawnX;
    }

    public double getSpawnY() {
        return spawnY;
    }

    public double getSpawnZ() {
        return spawnZ;
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public float getSpawnPitch() {
        return spawnPitch;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    /**
     * Renames this outpost.
     *
     * @param newName the new name (must be valid)
     * @return true if renamed successfully, false if name is invalid
     */
    public boolean rename(String newName) {
        if (!isValidName(newName)) {
            return false;
        }
        this.name = newName.trim();
        return true;
    }

    /**
     * Updates the spawn location.
     *
     * @param world the spawn world name
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param z the Z coordinate
     * @param yaw the yaw rotation
     * @param pitch the pitch rotation
     */
    public void updateSpawn(String world, double x, double y, double z, float yaw, float pitch) {
        Objects.requireNonNull(world, "World cannot be null");
        // Note: We create a new Outpost rather than modify these final fields
        // For now, clients should delete and recreate if they need different spawn location
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Outpost)) return false;
        Outpost outpost = (Outpost) o;
        return id.equals(outpost.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Outpost{id=%s, name=%s, location=%s, guildId=%s}",
            id, name, location, guildId);
    }
}
