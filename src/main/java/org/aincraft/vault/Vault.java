package org.aincraft.vault;

import java.util.UUID;
import org.aincraft.multiblock.Rotation;
import org.bukkit.inventory.ItemStack;

/**
 * Represents a guild vault with storage contents.
 */
public class Vault {
    public static final int STORAGE_SIZE = 54; // 6 rows

    private final String id;
    private final UUID guildId;
    private final String world;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final Rotation rotation;
    private final UUID createdBy;
    private final long createdAt;
    private ItemStack[] contents;

    /**
     * Creates a new vault.
     */
    public Vault(UUID guildId, String world, int originX, int originY, int originZ,
                 Rotation rotation, UUID createdBy) {
        this.id = UUID.randomUUID().toString();
        this.guildId = guildId;
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.rotation = rotation;
        this.createdBy = createdBy;
        this.createdAt = System.currentTimeMillis();
        this.contents = new ItemStack[STORAGE_SIZE];
    }

    /**
     * Creates a vault from database values.
     */
    public Vault(String id, UUID guildId, String world, int originX, int originY, int originZ,
                 Rotation rotation, UUID createdBy, long createdAt, ItemStack[] contents) {
        this.id = id;
        this.guildId = guildId;
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.rotation = rotation;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.contents = contents != null ? contents : new ItemStack[STORAGE_SIZE];
    }

    public String getId() {
        return id;
    }

    public UUID getGuildId() {
        return guildId;
    }

    public String getWorld() {
        return world;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    public Rotation getRotation() {
        return rotation;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public ItemStack[] getContents() {
        return contents;
    }

    public void setContents(ItemStack[] contents) {
        this.contents = contents != null ? contents : new ItemStack[STORAGE_SIZE];
    }
}
