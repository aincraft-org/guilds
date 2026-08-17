package dev.mintychochip.territory.storage;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

/** Marks a virtual guild-storage chest. */
public final class GuildStorageHolder implements InventoryHolder {
    private final UUID viewer;
    private final String guildId;
    private final int revision;
    private final boolean canDeposit;
    private final boolean canWithdraw;
    private Inventory inventory;

    /**
     * Creates a holder for one open session.
     *
     * @param viewer player
     * @param guildId owning guild
     * @param revision opened revision
     * @param canDeposit whether the viewer may add items
     * @param canWithdraw whether the viewer may take items
     */
    public GuildStorageHolder(UUID viewer, String guildId, int revision,
                              boolean canDeposit, boolean canWithdraw) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.guildId = Objects.requireNonNull(guildId, "guildId");
        this.revision = revision;
        this.canDeposit = canDeposit;
        this.canWithdraw = canWithdraw;
    }

    /**
     * Binds the created inventory.
     *
     * @param inventory chest inventory
     */
    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Returns the viewer.
     *
     * @return player id
     */
    public UUID viewer() {
        return viewer;
    }

    /**
     * Returns the owning guild.
     *
     * @return guild id
     */
    public String guildId() {
        return guildId;
    }

    /**
     * Returns the opened revision.
     *
     * @return revision
     */
    public int revision() {
        return revision;
    }

    /**
     * Returns whether the viewer may add items.
     *
     * @return deposit allowed
     */
    public boolean canDeposit() {
        return canDeposit;
    }

    /**
     * Returns whether the viewer may take items.
     *
     * @return withdraw allowed
     */
    public boolean canWithdraw() {
        return canWithdraw;
    }
}
