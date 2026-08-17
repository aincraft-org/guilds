package dev.mintychochip.territory.storage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

/** Virtual 54-slot guild item bank opened at a storage facility. */
public final class GuildStorageGui implements Listener, GuildStorageAnchorHandler {
    private final Plugin plugin;
    private final GuildStorageService storage;
    private final PaperItemCodec codec;

    /**
     * Creates the GUI.
     *
     * @param plugin plugin
     * @param storage storage service
     * @param codec item codec
     */
    public GuildStorageGui(Plugin plugin, GuildStorageService storage, PaperItemCodec codec) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public void open(Player player, String worldId, int x, int y, int z) {
        Objects.requireNonNull(player, "player");
        StorageResult result = storage.open(player.getUniqueId(), worldId, x, y, z);
        if (!result.succeeded() || result.snapshot() == null) {
            player.sendMessage(Component.text(message(result.status()), NamedTextColor.RED));
            return;
        }
        StorageSnapshot snapshot = result.snapshot();
        for (StorageSlot slot : snapshot.slots()) {
            if (codec.decode(slot.item()).isEmpty()) {
                storage.close(player.getUniqueId(), snapshot.guildId());
                player.sendMessage(Component.text("Guild storage contains an unreadable item.",
                        NamedTextColor.RED));
                return;
            }
        }
        GuildStorageHolder holder = new GuildStorageHolder(
                player.getUniqueId(), snapshot.guildId(), snapshot.revision(),
                snapshot.canDeposit(), snapshot.canWithdraw());
        Inventory inventory = Bukkit.createInventory(holder, snapshot.capacitySlots(),
                Component.text("Guild Storage"));
        holder.bind(inventory);
        for (StorageSlot slot : snapshot.slots()) {
            codec.decode(slot.item()).ifPresent(item -> inventory.setItem(slot.index(), item));
        }
        player.openInventory(inventory);
    }

    /**
     * Enforces deposit/withdraw on the storage chest.
     *
     * @param event click
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuildStorageHolder holder)) {
            return;
        }
        if (!holder.viewer().equals(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        boolean inStorage = raw >= 0 && raw < event.getView().getTopInventory().getSize();
        if (inStorage && !holder.canWithdraw() && hasItem(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        if (inStorage && !holder.canDeposit() && hasItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (!inStorage && !holder.canDeposit() && event.isShiftClick() && hasItem(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        if (!holder.canDeposit() && (event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY)
                && !inStorage) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocks drags that would deposit without permission.
     *
     * @param event drag
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuildStorageHolder holder)) {
            return;
        }
        int size = event.getView().getTopInventory().getSize();
        boolean touchesStorage = event.getRawSlots().stream().anyMatch(slot -> slot < size);
        if (touchesStorage && !holder.canDeposit()) {
            event.setCancelled(true);
        }
    }

    /**
     * Persists the chest and releases the session.
     *
     * @param event close
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuildStorageHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)
                || !holder.viewer().equals(player.getUniqueId())) {
            return;
        }
        List<StorageSlot> slots = new ArrayList<>();
        ItemStack[] contents = event.getInventory().getContents();
        for (int index = 0; index < contents.length; index++) {
            Optional<OpaqueItemPayload> encoded = codec.encode(contents[index]);
            if (encoded.isPresent()) {
                slots.add(new StorageSlot(index, encoded.get()));
            }
        }
        StorageResult saved = storage.save(player.getUniqueId(), holder.guildId(), holder.revision(), slots);
        if (!saved.succeeded()) {
            plugin.getLogger().log(Level.WARNING, "Failed to save guild storage for " + holder.guildId());
            player.sendMessage(Component.text("Could not save guild storage.", NamedTextColor.RED));
            drop(player, contents);
        }
        storage.close(player.getUniqueId(), holder.guildId());
    }

    /**
     * Releases a disconnected viewer.
     *
     * @param event quit
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (storage instanceof GuildStorageEngine engine) {
            engine.release(event.getPlayer().getUniqueId());
        }
    }

    private static void drop(Player player, ItemStack[] contents) {
        if (player.getWorld() == null) {
            return;
        }
        for (ItemStack item : contents) {
            if (hasItem(item)) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }

    private static boolean hasItem(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.getAmount() > 0;
    }

    private static String message(StorageStatus status) {
        return switch (status) {
            case DENIED_NO_FACILITY -> "Stand at a guild storage building to open the item bank.";
            case DENIED_WRONG_TYPE -> "That building is not a storage vault.";
            case DENIED_NO_GOVERNMENT -> "This territory has no governing guild.";
            case DENIED_NOT_RESIDENT -> "Only guild members can use this storage.";
            case DENIED_NO_PERMISSION -> "You cannot use guild storage.";
            case DENIED_IN_USE -> "Someone else is using this guild storage.";
            default -> "Guild storage is unavailable.";
        };
    }
}
