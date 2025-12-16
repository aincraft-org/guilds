package org.aincraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Guild {
    private static final int DEFAULT_MAX_MEMBERS = 100;
    private static final int MIN_MAX_MEMBERS = 1;

    private final String id;
    private String name;
    private String description;
    private UUID ownerId;
    private final List<UUID> members;
    private final long createdAt;
    private int maxMembers;

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
     */
    public Guild(String id, String name, String description, UUID ownerId, long createdAt, int maxMembers) {
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