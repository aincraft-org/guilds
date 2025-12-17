package org.aincraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Guild {
    private static final int DEFAULT_MAX_MEMBERS = 100;
    private static final int MIN_MAX_MEMBERS = 1;
    private static final int CHUNK_SHIFT = 4; // Block coordinates to chunk coordinates (right shift 4 = divide by 16)

    private final String id;
    private String name;
    private String description;
    private UUID ownerId;
    private final List<UUID> members;
    private final long createdAt;
    private int maxMembers;
    private String spawnWorld;
    private Double spawnX;
    private Double spawnY;
    private Double spawnZ;
    private Float spawnYaw;
    private Float spawnPitch;
    private String color;
    private String homeblockWorld;
    private Integer homeblockChunkX;
    private Integer homeblockChunkZ;
    private boolean allowExplosions;
    private boolean allowFire;

    /**
     * Creates a new Guild with the given parameters.
     *
     * @param name the guild name (cannot be null or empty)
     * @param description the guild description (can be null)
     * @param ownerId the UUID of the guild owner (cannot be null)
     * @throws IllegalArgumentException if name is null/empty or ownerId is null
     */
    public Guild(String name, String description, UUID ownerId) {
        this.id = UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(name, "Guild name cannot be null");
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Guild name cannot be empty");
        }
        this.description = description;
        this.ownerId = Objects.requireNonNull(ownerId, "Owner ID cannot be null");
        this.members = new ArrayList<>();
        this.members.add(ownerId);
        this.createdAt = System.currentTimeMillis();
        this.maxMembers = DEFAULT_MAX_MEMBERS;
        this.allowExplosions = true;
        this.allowFire = true;
    }

    /**
     * Creates a Guild with existing data (for database restoration).
     *
     * @param id the existing guild ID
     * @param name the guild name
     * @param description the guild description
     * @param ownerId the guild owner UUID
     * @param createdAt the creation timestamp
     * @param maxMembers the max members limit
     * @param color the guild color (can be null)
     */
    public Guild(String id, String name, String description, UUID ownerId, long createdAt, int maxMembers, String color) {
        this.id = Objects.requireNonNull(id, "Guild ID cannot be null");
        this.name = Objects.requireNonNull(name, "Guild name cannot be null");
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Guild name cannot be empty");
        }
        this.description = description;
        this.ownerId = Objects.requireNonNull(ownerId, "Owner ID cannot be null");
        this.members = new ArrayList<>();
        this.createdAt = createdAt;
        this.maxMembers = maxMembers;
        this.color = color;
        this.allowExplosions = true;
        this.allowFire = true;
    }

    /**
     * Factory method to create a new Guild with validation.
     *
     * @param name the guild name (cannot be null or empty)
     * @param description the guild description (can be null)
     * @param ownerId the UUID of the guild owner (cannot be null)
     * @return a new Guild instance
     * @throws IllegalArgumentException if name is null/empty or ownerId is null
     */
    public static Guild createGuild(String name, String description, UUID ownerId) {
        return new Guild(name, description, ownerId);
    }

    /**
     * Attempts to add a player to this guild.
     *
     * @param playerId the UUID of the player to add (cannot be null)
     * @return true if the player was successfully added, false if they're already a member or guild is full
     * @throws IllegalArgumentException if playerId is null
     */
    public boolean joinGuild(UUID playerId) {
        validatePlayerId(playerId);
        if (isMemberFull()) {
            return false;
        }
        if (members.contains(playerId)) {
            return false;
        }
        return members.add(playerId);
    }

    /**
     * Attempts to remove a player from this guild.
     * The owner cannot leave the guild.
     *
     * @param playerId the UUID of the player to remove (cannot be null)
     * @return true if the player was successfully removed, false if they're the owner or not a member
     * @throws IllegalArgumentException if playerId is null
     */
    public boolean leaveGuild(UUID playerId) {
        validatePlayerId(playerId);
        if (isOwner(playerId)) {
            return false;
        }
        return members.remove(playerId);
    }

    /**
     * Attempts to kick a member from this guild.
     * Only the owner can kick members, and the owner cannot be kicked.
     *
     * @param kickerId the UUID of the player attempting to kick (cannot be null)
     * @param targetId the UUID of the player to be kicked (cannot be null)
     * @return true if the member was successfully kicked, false if kicker is not owner or target is owner
     * @throws IllegalArgumentException if kickerId or targetId is null
     */
    public boolean kickMember(UUID kickerId, UUID targetId) {
        validatePlayerId(kickerId);
        validatePlayerId(targetId);

        if (!isOwner(kickerId)) {
            return false;
        }
        if (isOwner(targetId)) {
            return false;
        }
        return members.remove(targetId);
    }

    /**
     * Checks if the requesting player can delete this guild (must be owner).
     *
     * @param requestingPlayerId the UUID of the player requesting deletion (cannot be null)
     * @return true if the requesting player is the guild owner, false otherwise
     */
    public boolean deleteGuild(UUID requestingPlayerId) {
        return isOwner(requestingPlayerId);
    }

    /**
     * Checks if the given player is the guild owner.
     *
     * @param playerId the UUID of the player to check (cannot be null)
     * @return true if the player is the owner, false otherwise
     */
    public boolean isOwner(UUID playerId) {
        return ownerId.equals(Objects.requireNonNull(playerId));
    }

    /**
     * Checks if the given player is a member of this guild.
     *
     * @param playerId the UUID of the player to check (cannot be null)
     * @return true if the player is a member, false otherwise
     */
    public boolean isMember(UUID playerId) {
        return members.contains(Objects.requireNonNull(playerId));
    }

    /**
     * Gets the current number of members in this guild.
     *
     * @return the member count
     */
    public int getMemberCount() {
        return members.size();
    }

    /**
     * Attempts to transfer guild ownership to a new owner.
     * The new owner must already be a member of the guild.
     *
     * @param newOwnerId the UUID of the new owner (cannot be null)
     * @return true if ownership was successfully transferred, false if the new owner is not a member
     * @throws IllegalArgumentException if newOwnerId is null
     */
    public boolean transferOwnership(UUID newOwnerId) {
        validatePlayerId(newOwnerId);
        if (!members.contains(newOwnerId)) {
            return false;
        }
        this.ownerId = newOwnerId;
        return true;
    }

    /**
     * Sets the maximum number of members allowed in this guild.
     *
     * @param maxMembers the new maximum member count (must be at least 1 and >= current member count)
     * @throws IllegalArgumentException if maxMembers is less than 1 or less than current member count
     */
    public void setMaxMembers(int maxMembers) {
        if (maxMembers < MIN_MAX_MEMBERS) {
            throw new IllegalArgumentException("Max members must be at least " + MIN_MAX_MEMBERS);
        }
        if (maxMembers < members.size()) {
            throw new IllegalArgumentException("Cannot set max members lower than current member count");
        }
        this.maxMembers = maxMembers;
    }

    /**
     * Checks if the guild is at maximum capacity.
     *
     * @return true if member count >= max members, false otherwise
     */
    private boolean isMemberFull() {
        return members.size() >= maxMembers;
    }

    /**
     * Validates that a player ID is not null.
     *
     * @param playerId the UUID to validate
     * @throws IllegalArgumentException if playerId is null
     */
    private void validatePlayerId(UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
    }

    /**
     * Gets the unique identifier of this guild.
     *
     * @return the guild ID (immutable)
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the name of this guild.
     *
     * @return the guild name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this guild.
     *
     * @param name the new name (cannot be null or empty)
     * @throws IllegalArgumentException if name is null or empty
     */
    public void setName(String name) {
        String validatedName = Objects.requireNonNull(name, "Guild name cannot be null");
        if (validatedName.trim().isEmpty()) {
            throw new IllegalArgumentException("Guild name cannot be empty");
        }
        this.name = validatedName;
    }

    /**
     * Gets the description of this guild.
     *
     * @return the guild description (can be null)
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of this guild.
     *
     * @param description the new description (can be null)
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the UUID of the guild owner.
     *
     * @return the owner's UUID
     */
    public UUID getOwnerId() {
        return ownerId;
    }

    /**
     * Gets an unmodifiable copy of the members list.
     * Modifications to the returned list will not affect the guild.
     *
     * @return an unmodifiable list of member UUIDs
     */
    public List<UUID> getMembers() {
        return Collections.unmodifiableList(new ArrayList<>(members));
    }

    /**
     * Gets the creation timestamp of this guild.
     *
     * @return the creation time in milliseconds since epoch
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the maximum number of members allowed in this guild.
     *
     * @return the maximum member count
     */
    public int getMaxMembers() {
        return maxMembers;
    }

    /**
     * Compares this guild to another object for equality.
     * Guilds are equal if they have the same ID.
     *
     * @param o the object to compare with
     * @return true if both objects are guilds with the same ID, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Guild)) return false;
        Guild guild = (Guild) o;
        return Objects.equals(id, guild.id);
    }

    /**
     * Returns the hash code of this guild based on its ID.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Checks if this guild has a spawn location set.
     *
     * @return true if spawn is set, false otherwise
     */
    public boolean hasSpawn() {
        return spawnWorld != null;
    }

    /**
     * Sets the spawn location for this guild from a Bukkit Location.
     *
     * @param location the location to set as spawn (cannot be null)
     * @throws IllegalArgumentException if location is null
     */
    public void setSpawn(org.bukkit.Location location) {
        Objects.requireNonNull(location, "Location cannot be null");
        this.spawnWorld = location.getWorld().getName();
        this.spawnX = location.getX();
        this.spawnY = location.getY();
        this.spawnZ = location.getZ();
        this.spawnYaw = (float) location.getYaw();
        this.spawnPitch = (float) location.getPitch();
    }

    /**
     * Clears the spawn location for this guild.
     */
    public void clearSpawn() {
        this.spawnWorld = null;
        this.spawnX = null;
        this.spawnY = null;
        this.spawnZ = null;
        this.spawnYaw = null;
        this.spawnPitch = null;
    }

    /**
     * Gets the spawn world name.
     *
     * @return the world name, or null if no spawn set
     */
    public String getSpawnWorld() {
        return spawnWorld;
    }

    /**
     * Sets the spawn world name.
     *
     * @param spawnWorld the world name
     */
    public void setSpawnWorld(String spawnWorld) {
        this.spawnWorld = spawnWorld;
    }

    /**
     * Gets the spawn X coordinate.
     *
     * @return the X coordinate, or null if no spawn set
     */
    public Double getSpawnX() {
        return spawnX;
    }

    /**
     * Sets the spawn X coordinate.
     *
     * @param spawnX the X coordinate
     */
    public void setSpawnX(Double spawnX) {
        this.spawnX = spawnX;
    }

    /**
     * Gets the spawn Y coordinate.
     *
     * @return the Y coordinate, or null if no spawn set
     */
    public Double getSpawnY() {
        return spawnY;
    }

    /**
     * Sets the spawn Y coordinate.
     *
     * @param spawnY the Y coordinate
     */
    public void setSpawnY(Double spawnY) {
        this.spawnY = spawnY;
    }

    /**
     * Gets the spawn Z coordinate.
     *
     * @return the Z coordinate, or null if no spawn set
     */
    public Double getSpawnZ() {
        return spawnZ;
    }

    /**
     * Sets the spawn Z coordinate.
     *
     * @param spawnZ the Z coordinate
     */
    public void setSpawnZ(Double spawnZ) {
        this.spawnZ = spawnZ;
    }

    /**
     * Gets the spawn yaw (rotation).
     *
     * @return the yaw, or null if no spawn set
     */
    public Float getSpawnYaw() {
        return spawnYaw;
    }

    /**
     * Sets the spawn yaw.
     *
     * @param spawnYaw the yaw value
     */
    public void setSpawnYaw(Float spawnYaw) {
        this.spawnYaw = spawnYaw;
    }

    /**
     * Gets the spawn pitch (vertical rotation).
     *
     * @return the pitch, or null if no spawn set
     */
    public Float getSpawnPitch() {
        return spawnPitch;
    }

    /**
     * Sets the spawn pitch.
     *
     * @param spawnPitch the pitch value
     */
    public void setSpawnPitch(Float spawnPitch) {
        this.spawnPitch = spawnPitch;
    }

    /**
     * Gets the guild color.
     *
     * @return the guild color (hex string or named color), or null if not set
     */
    public String getColor() {
        return color;
    }

    /**
     * Sets the guild color.
     *
     * @param color the guild color (hex string like #FF0000 or named color like 'red')
     */
    public void setColor(String color) {
        this.color = color;
    }

    /**
     * Checks if this guild has a homeblock set.
     *
     * @return true if homeblock is set, false otherwise
     */
    public boolean hasHomeblock() {
        return homeblockWorld != null;
    }

    /**
     * Gets the homeblock as a ChunkKey.
     *
     * @return the homeblock ChunkKey, or null if not set
     */
    public ChunkKey getHomeblock() {
        if (!hasHomeblock()) {
            return null;
        }
        return new ChunkKey(homeblockWorld, homeblockChunkX, homeblockChunkZ);
    }

    /**
     * Sets the homeblock for this guild.
     *
     * @param chunk the chunk to set as homeblock (cannot be null)
     * @throws IllegalArgumentException if chunk is null
     */
    public void setHomeblock(ChunkKey chunk) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");
        this.homeblockWorld = chunk.world();
        this.homeblockChunkX = chunk.x();
        this.homeblockChunkZ = chunk.z();
    }

    /**
     * Clears the homeblock for this guild.
     */
    public void clearHomeblock() {
        this.homeblockWorld = null;
        this.homeblockChunkX = null;
        this.homeblockChunkZ = null;
    }

    /**
     * Checks if a location is within the homeblock chunk.
     *
     * @param loc the location to check (cannot be null)
     * @return true if location is within homeblock, false otherwise
     * @throws IllegalArgumentException if loc is null
     */
    public boolean isInHomeblock(org.bukkit.Location loc) {
        Objects.requireNonNull(loc, "Location cannot be null");

        if (!hasHomeblock()) {
            return false;
        }

        if (!loc.getWorld().getName().equals(homeblockWorld)) {
            return false;
        }

        int chunkX = loc.getBlockX() >> CHUNK_SHIFT;
        int chunkZ = loc.getBlockZ() >> CHUNK_SHIFT;

        return chunkX == homeblockChunkX && chunkZ == homeblockChunkZ;
    }

    /**
     * Gets the homeblock world name.
     *
     * @return the world name, or null if no homeblock set
     */
    public String getHomeblockWorld() {
        return homeblockWorld;
    }

    /**
     * Gets the homeblock chunk X coordinate.
     *
     * @return the chunk X coordinate, or null if no homeblock set
     */
    public Integer getHomeblockX() {
        return homeblockChunkX;
    }

    /**
     * Gets the homeblock chunk Z coordinate.
     *
     * @return the chunk Z coordinate, or null if no homeblock set
     */
    public Integer getHomeblockZ() {
        return homeblockChunkZ;
    }

    /**
     * Checks if explosions are allowed in this guild's territory.
     *
     * @return true if explosions are allowed, false otherwise
     */
    public boolean isExplosionsAllowed() {
        return allowExplosions;
    }

    /**
     * Sets whether explosions are allowed in this guild's territory.
     *
     * @param allowExplosions true to allow explosions, false to prevent them
     */
    public void setExplosionsAllowed(boolean allowExplosions) {
        this.allowExplosions = allowExplosions;
    }

    /**
     * Checks if fire spread is allowed in this guild's territory.
     *
     * @return true if fire spread is allowed, false otherwise
     */
    public boolean isFireAllowed() {
        return allowFire;
    }

    /**
     * Sets whether fire spread is allowed in this guild's territory.
     *
     * @param allowFire true to allow fire spread, false to prevent it
     */
    public void setFireAllowed(boolean allowFire) {
        this.allowFire = allowFire;
    }

    /**
     * Returns a string representation of this guild.
     *
     * @return a string containing the guild's name and ID
     */
    @Override
    public String toString() {
        return "Guild{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", memberCount=" + members.size() +
                ", maxMembers=" + maxMembers +
                '}';
    }
}