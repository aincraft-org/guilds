package org.aincraft;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents default permissions for different guild relationship types.
 * Guilds can configure what permissions allies, enemies, and outsiders have
 * in their claimed territory.
 */
public final class GuildDefaultPermissions {
    private final UUID guildId;
    private int allyPermissions;
    private int enemyPermissions;
    private int outsiderPermissions;
    private final long createdAt;
    private long updatedAt;

    /**
     * Creates new default permissions for a guild.
     *
     * @param guildId the guild ID (cannot be null)
     * @param allyPermissions bitfield of permissions for allies
     * @param enemyPermissions bitfield of permissions for enemies
     * @param outsiderPermissions bitfield of permissions for outsiders
     */
    public GuildDefaultPermissions(
        UUID guildId,
        int allyPermissions,
        int enemyPermissions,
        int outsiderPermissions
    ) {
        this.guildId = Objects.requireNonNull(guildId, "Guild ID cannot be null");
        this.allyPermissions = allyPermissions;
        this.enemyPermissions = enemyPermissions;
        this.outsiderPermissions = outsiderPermissions;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public UUID getGuildId() {
        return guildId;
    }

    public int getAllyPermissions() {
        return allyPermissions;
    }

    public void setAllyPermissions(int allyPermissions) {
        this.allyPermissions = allyPermissions;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getEnemyPermissions() {
        return enemyPermissions;
    }

    public void setEnemyPermissions(int enemyPermissions) {
        this.enemyPermissions = enemyPermissions;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getOutsiderPermissions() {
        return outsiderPermissions;
    }

    public void setOutsiderPermissions(int outsiderPermissions) {
        this.outsiderPermissions = outsiderPermissions;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuildDefaultPermissions that = (GuildDefaultPermissions) o;
        return Objects.equals(guildId, that.guildId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(guildId);
    }

    @Override
    public String toString() {
        return "GuildDefaultPermissions{" +
            "guildId='" + guildId + '\'' +
            ", allyPermissions=" + allyPermissions +
            ", enemyPermissions=" + enemyPermissions +
            ", outsiderPermissions=" + outsiderPermissions +
            ", createdAt=" + createdAt +
            ", updatedAt=" + updatedAt +
            '}';
    }
}
