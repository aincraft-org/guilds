package org.aincraft.guilds.storage.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.guilds.storage.codec.ItemStackStorageCodec;
import org.aincraft.guilds.storage.persist.SqlGuildStorageStore;
import org.aincraft.guilds.storage.service.GuildStorageService;
import org.aincraft.guilds.storage.service.MainThreadExecutor;
import org.aincraft.guilds.storage.service.PlayerInventoryCoordinator;
import org.aincraft.guilds.storage.service.StorageResult;
import org.aincraft.guilds.storage.service.impl.GuildStorageServiceImpl;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.aincraft.guilds.territory.storage.StorageSlot;
import org.aincraft.guilds.territory.storage.StorageTab;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread guild storage chest backed by {@link GuildStorageService}. */
public final class GuildStorageGUI implements InventoryHolder, Listener {
    public static final int LOGICAL_SLOTS = 54;

    private final GuildStorageService storageService;
    private final PlayerInventoryCoordinator inventoryCoordinator;
    private final MainThreadExecutor mainThreadExecutor;
    private final ItemStackStorageCodec codec;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private Inventory inventory;

    public GuildStorageGUI(
            JavaPlugin plugin,
            GuildStorageService storageService,
            PlayerInventoryCoordinator inventoryCoordinator,
            MainThreadExecutor mainThreadExecutor) {
        Objects.requireNonNull(plugin, "plugin");
        this.storageService = Objects.requireNonNull(storageService, "storageService");
        this.inventoryCoordinator = Objects.requireNonNull(inventoryCoordinator, "inventoryCoordinator");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.codec = new ItemStackStorageCodec();
    }

    public void open(Player player, SettlementFacility facility, String guildId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(facility, "facility");
        Objects.requireNonNull(guildId, "guildId");
        mainThreadExecutor.run(() -> openOnMainThread(player, facility, guildId));
    }

    private void openOnMainThread(Player player, SettlementFacility facility, String guildId) {
        StorageResult<List<StorageTab>> tabs = storageService.getTabs(player.getUniqueId(), guildId);
        if (!tabs.isSuccess()) {
            player.sendMessage(Component.text(tabs.errorMessage(), NamedTextColor.RED));
            return;
        }
        StorageTab tab = tabs.value().orElseThrow().stream()
                .filter(candidate -> SqlGuildStorageStore.DEFAULT_TAB_ID.equals(candidate.tabId()))
                .findFirst()
                .orElse(tabs.value().orElseThrow().get(0));

        StorageResult<Map<Integer, StorageSlot>> slots =
                storageService.getSlots(player.getUniqueId(), guildId, tab.tabId());
        if (!slots.isSuccess()) {
            player.sendMessage(Component.text(slots.errorMessage(), NamedTextColor.RED));
            return;
        }

        inventory = Bukkit.createInventory(
                this,
                LOGICAL_SLOTS,
                Component.text(facility.name() + " Storage", NamedTextColor.GOLD));
        renderSlots(slots.value().orElseThrow());

        sessions.put(player.getUniqueId(), new Session(player.getUniqueId(), facility, guildId, tab.tabId()));
        player.openInventory(inventory);
    }

    private void renderSlots(Map<Integer, StorageSlot> slots) {
        inventory.clear();
        for (Map.Entry<Integer, StorageSlot> entry : slots.entrySet()) {
            int slotIndex = entry.getKey();
            if (slotIndex < 0 || slotIndex >= LOGICAL_SLOTS) {
                continue;
            }
            inventory.setItem(slotIndex, codec.decode(entry.getValue().item()));
        }
    }

    private void refreshSession(Player player, Session session) {
        StorageResult<Map<Integer, StorageSlot>> slots =
                storageService.getSlots(player.getUniqueId(), session.guildId(), session.tabId());
        if (slots.isSuccess()) {
            renderSlots(slots.value().orElseThrow());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuildStorageGUI)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !Objects.equals(event.getInventory(), inventory)) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= LOGICAL_SLOTS) {
            return;
        }

        int slotIndex = event.getRawSlot();
        ItemStack cursor = event.getCursor();
        ItemStack stored = inventory.getItem(slotIndex);
        boolean cursorEmpty = cursor == null || cursor.getType() == Material.AIR;
        boolean storedEmpty = stored == null || stored.getType() == Material.AIR;

        if (!cursorEmpty && storedEmpty) {
            deposit(player, session, slotIndex, cursor.clone());
            return;
        }
        if (cursorEmpty && !storedEmpty) {
            withdraw(player, session, slotIndex);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuildStorageGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof GuildStorageGUI) {
            sessions.remove(player.getUniqueId());
        }
    }

    private void deposit(Player player, Session session, int slotIndex, ItemStack item) {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = codec.encode(item);
        Runnable restoreOnFailure = () -> inventoryCoordinator.giveItem(
                player.getUniqueId(),
                item.clone(),
                success -> {
                    if (!success) {
                        mainThreadExecutor.run(() -> {
                            ItemStack current = player.getItemOnCursor();
                            if (current == null || current.getType() == Material.AIR) {
                                player.setItemOnCursor(item.clone());
                            }
                        });
                    }
                });

        player.setItemOnCursor(null);
        StorageResult<StorageSlot> result = invokeDeposit(
                operationId,
                player.getUniqueId(),
                session.guildId(),
                session.tabId(),
                slotIndex,
                payload,
                session.facility().id(),
                restoreOnFailure);

        if (!result.isSuccess()) {
            player.sendMessage(Component.text(result.errorMessage(), NamedTextColor.RED));
        }
        refreshSession(player, session);
    }

    private void withdraw(Player player, Session session, int slotIndex) {
        UUID operationId = UUID.randomUUID();
        ItemStack pending = inventory.getItem(slotIndex);
        if (pending == null || pending.getType() == Material.AIR) {
            return;
        }
        ItemStack payout = pending.clone();
        Runnable restoreOnFailure = () -> inventoryCoordinator.removeMatching(
                player.getUniqueId(), payout.clone(), ignored -> {});

        StorageResult<OpaqueItemPayload> result = invokeWithdraw(
                operationId,
                player.getUniqueId(),
                session.guildId(),
                session.tabId(),
                slotIndex,
                session.facility().id(),
                restoreOnFailure);

        if (!result.isSuccess()) {
            player.sendMessage(Component.text(result.errorMessage(), NamedTextColor.RED));
            refreshSession(player, session);
            return;
        }

        ItemStack decoded = codec.decode(result.value().orElseThrow());
        inventoryCoordinator.giveItem(player.getUniqueId(), decoded, success -> mainThreadExecutor.run(() -> {
            if (!success) {
                player.sendMessage(Component.text("Not enough inventory space.", NamedTextColor.RED));
            }
            refreshSession(player, session);
        }));
    }

    private StorageResult<StorageSlot> invokeDeposit(
            UUID operationId,
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            String facilityId,
            Runnable compensationOnFailure) {
        if (storageService instanceof GuildStorageServiceImpl impl) {
            return impl.depositWithCompensation(
                    actor, guildId, tabId, slotIndex, item, facilityId, operationId, compensationOnFailure);
        }
        return storageService.deposit(operationId, actor, guildId, tabId, slotIndex, item, facilityId);
    }

    private StorageResult<OpaqueItemPayload> invokeWithdraw(
            UUID operationId,
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            String facilityId,
            Runnable compensationOnFailure) {
        if (storageService instanceof GuildStorageServiceImpl impl) {
            return impl.withdrawWithCompensation(
                    actor, guildId, tabId, slotIndex, facilityId, operationId, compensationOnFailure);
        }
        return storageService.withdraw(operationId, actor, guildId, tabId, slotIndex, facilityId);
    }

    Session sessionFor(UUID playerId) {
        return sessions.get(playerId);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    record Session(UUID playerId, SettlementFacility facility, String guildId, String tabId) {}
}
