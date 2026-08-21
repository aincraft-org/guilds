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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-player guild storage chest backed by {@link GuildStorageService}. */
public final class GuildStorageGUI implements InventoryHolder, Listener {
    public static final int LOGICAL_SLOTS = 54;

    private final GuildStorageService storageService;
    private final PlayerInventoryCoordinator inventoryCoordinator;
    private final MainThreadExecutor mainThreadExecutor;
    private final Executor sqlExecutor;
    private final ItemStackStorageCodec codec;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private volatile Inventory inventory;

    public GuildStorageGUI(
            JavaPlugin plugin,
            GuildStorageService storageService,
            PlayerInventoryCoordinator inventoryCoordinator,
            MainThreadExecutor mainThreadExecutor) {
        this(plugin, storageService, inventoryCoordinator, mainThreadExecutor, Runnable::run);
    }

    public GuildStorageGUI(
            JavaPlugin plugin,
            GuildStorageService storageService,
            PlayerInventoryCoordinator inventoryCoordinator,
            MainThreadExecutor mainThreadExecutor,
            Executor sqlExecutor) {
        Objects.requireNonNull(plugin, "plugin");
        this.storageService = Objects.requireNonNull(storageService, "storageService");
        this.inventoryCoordinator = Objects.requireNonNull(inventoryCoordinator, "inventoryCoordinator");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.sqlExecutor = Objects.requireNonNull(sqlExecutor, "sqlExecutor");
        this.codec = new ItemStackStorageCodec();
    }

    public void open(Player player, SettlementFacility facility, String guildId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(facility, "facility");
        Objects.requireNonNull(guildId, "guildId");
        UUID playerId = player.getUniqueId();
        CompletableFuture.supplyAsync(() -> loadOpenState(playerId, guildId), sqlExecutor)
                .thenAcceptAsync(
                        state -> finishOpen(player, facility, guildId, state, null),
                        command -> mainThreadExecutor.run(command))
                .exceptionally(error -> {
                    mainThreadExecutor.run(() -> finishOpen(player, facility, guildId, null, error));
                    return null;
                });
    }

    private OpenState loadOpenState(UUID playerId, String guildId) {
        StorageResult<List<StorageTab>> tabs = storageService.getTabs(playerId, guildId);
        if (!tabs.isSuccess()) {
            return OpenState.failure(tabs.errorMessage());
        }
        StorageTab tab = tabs.value().orElseThrow().stream()
                .filter(candidate -> SqlGuildStorageStore.DEFAULT_TAB_ID.equals(candidate.tabId()))
                .findFirst()
                .orElse(tabs.value().orElseThrow().get(0));
        StorageResult<Map<Integer, StorageSlot>> slots =
                storageService.getSlots(playerId, guildId, tab.tabId());
        if (!slots.isSuccess()) {
            return OpenState.failure(slots.errorMessage());
        }
        return OpenState.success(tab.tabId(), slots.value().orElseThrow());
    }

    private void finishOpen(
            Player player,
            SettlementFacility facility,
            String guildId,
            OpenState state,
            Throwable error) {
        if (error != null) {
            player.sendMessage(Component.text("Failed to open guild storage.", NamedTextColor.RED));
            return;
        }
        if (state == null || !state.success()) {
            player.sendMessage(Component.text(
                    state == null ? "Failed to open guild storage." : state.errorMessage(), NamedTextColor.RED));
            return;
        }

        Inventory sessionInventory = Bukkit.createInventory(
                this,
                LOGICAL_SLOTS,
                Component.text(facility.name() + " Storage", NamedTextColor.GOLD));
        renderSlots(sessionInventory, state.slots());
        inventory = sessionInventory;
        sessions.put(
                player.getUniqueId(),
                new Session(player.getUniqueId(), facility, guildId, state.tabId(), sessionInventory));
        player.openInventory(sessionInventory);
    }

    private void renderSlots(Inventory target, Map<Integer, StorageSlot> slots) {
        target.clear();
        for (Map.Entry<Integer, StorageSlot> entry : slots.entrySet()) {
            int slotIndex = entry.getKey();
            if (slotIndex < 0 || slotIndex >= LOGICAL_SLOTS) {
                continue;
            }
            target.setItem(slotIndex, codec.decode(entry.getValue().item()));
        }
    }

    private void refreshSession(Player player, Session session) {
        try {
            CompletableFuture.supplyAsync(
                            () -> storageService.getSlots(player.getUniqueId(), session.guildId(), session.tabId()),
                            sqlExecutor)
                    .thenAcceptAsync(
                            slots -> {
                                Session active = sessions.get(player.getUniqueId());
                                if (active == null || active != session) {
                                    return;
                                }
                                if (slots.isSuccess()) {
                                    renderSlots(session.inventory(), slots.value().orElseThrow());
                                }
                            },
                            command -> mainThreadExecutor.run(command));
        } catch (Throwable ignored) {
            // Best-effort refresh; ignore executor submission failures.
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GuildStorageGUI)) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !Objects.equals(event.getInventory(), session.inventory())) {
            return;
        }
        if (isBlockedStorageInteraction(event)) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= LOGICAL_SLOTS) {
            return;
        }

        event.setCancelled(true);
        int slotIndex = event.getRawSlot();
        ItemStack cursor = event.getCursor();
        ItemStack stored = session.inventory().getItem(slotIndex);
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
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null || event.getView() == null
                || !Objects.equals(event.getView().getTopInventory(), session.inventory())) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < LOGICAL_SLOTS) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GuildStorageGUI)) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session != null && Objects.equals(event.getInventory(), session.inventory())) {
            sessions.remove(player.getUniqueId());
        }
    }

    private static boolean isBlockedStorageInteraction(InventoryClickEvent event) {
        ClickType click = event.getClick();
        if (click != null
                && (click == ClickType.SHIFT_LEFT
                        || click == ClickType.SHIFT_RIGHT
                        || click == ClickType.NUMBER_KEY
                        || click == ClickType.DOUBLE_CLICK)) {
            return true;
        }
        InventoryAction action = event.getAction();
        if (action == null) {
            return false;
        }
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.HOTBAR_MOVE_AND_READD
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.COLLECT_TO_CURSOR;
    }

    private void deposit(Player player, Session session, int slotIndex, ItemStack item) {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = codec.encode(item);
        ItemStack retainedItem = item.clone();
        AtomicBoolean restored = new AtomicBoolean(false);
        Runnable restoreOnFailure = () -> {
            if (!restored.compareAndSet(false, true)) {
                return;
            }
            mainThreadExecutor.run(() -> restoreDepositItem(player, retainedItem, operationId));
        };

        player.setItemOnCursor(null);
        Runnable notifyDepositFailure = () -> mainThreadExecutor.run(() -> {
            if (activeSession(player, session)) {
                player.sendMessage(Component.text("Failed to deposit item.", NamedTextColor.RED));
                refreshSession(player, session);
            }
        });
        Runnable handleDepositFailure = () -> {
            restoreOnFailure.run();
            notifyDepositFailure.run();
        };
        try {
            CompletableFuture.supplyAsync(
                            () -> invokeDeposit(
                                    operationId,
                                    player.getUniqueId(),
                                    session.guildId(),
                                    session.tabId(),
                                    slotIndex,
                                    payload,
                                    session.facility().id(),
                                    restoreOnFailure),
                            sqlExecutor)
                    .thenAcceptAsync(
                            result -> handleDepositResult(player, session, result),
                            command -> mainThreadExecutor.run(command))
                    .exceptionally(error -> {
                        handleDepositFailure.run();
                        return null;
                    });
        } catch (Throwable error) {
            handleDepositFailure.run();
        }
    }

    private void handleDepositResult(Player player, Session session, StorageResult<StorageSlot> result) {
        if (!result.isSuccess()) {
            if (activeSession(player, session)) {
                player.sendMessage(Component.text(result.errorMessage(), NamedTextColor.RED));
                refreshSession(player, session);
            }
            return;
        }
        if (activeSession(player, session)) {
            refreshSession(player, session);
        }
    }

    private void withdraw(Player player, Session session, int slotIndex) {
        UUID operationId = UUID.randomUUID();
        ItemStack pending = session.inventory().getItem(slotIndex);
        if (pending == null || pending.getType() == Material.AIR) {
            return;
        }
        CompletableFuture.supplyAsync(
                        () -> invokeWithdraw(
                                operationId,
                                player.getUniqueId(),
                                session.guildId(),
                                session.tabId(),
                                slotIndex,
                                session.facility().id(),
                                null),
                        sqlExecutor)
                .thenAcceptAsync(
                        result -> handleWithdrawResult(player, session, operationId, slotIndex, result),
                        command -> mainThreadExecutor.run(command))
                .exceptionally(error -> {
                    mainThreadExecutor.run(() -> {
                        if (activeSession(player, session)) {
                            player.sendMessage(Component.text("Failed to withdraw item.", NamedTextColor.RED));
                            refreshSession(player, session);
                        }
                    });
                    return null;
                });
    }

    private void handleWithdrawResult(
            Player player,
            Session session,
            UUID operationId,
            int slotIndex,
            StorageResult<OpaqueItemPayload> result) {
        if (!result.isSuccess()) {
            if (activeSession(player, session)) {
                player.sendMessage(Component.text(result.errorMessage(), NamedTextColor.RED));
                refreshSession(player, session);
            }
            return;
        }

        OpaqueItemPayload payload = result.value().orElseThrow();
        ItemStack decoded = codec.decode(payload);
        if (!(storageService instanceof GuildStorageServiceImpl payoutService)) {
            inventoryCoordinator.giveItem(
                    player.getUniqueId(),
                    decoded,
                    payoutSuccess -> mainThreadExecutor.run(() -> {
                        if (payoutSuccess && activeSession(player, session)) {
                            refreshSession(player, session);
                        }
                    }));
            return;
        }
        CompletableFuture.supplyAsync(() -> payoutService.beginWithdrawPayoutDelivery(operationId), sqlExecutor)
                .thenAcceptAsync(
                        claimResult -> mainThreadExecutor.run(() -> {
                            if (!claimResult.isSuccess()) {
                                if (activeSession(player, session)) {
                                    player.sendMessage(Component.text(claimResult.errorMessage(), NamedTextColor.RED));
                                    refreshSession(player, session);
                                }
                                return;
                            }
                            UUID deliveryToken = claimResult.value().orElseThrow().deliveryToken();
                            inventoryCoordinator.giveItem(
                                    player.getUniqueId(),
                                    decoded,
                                    payoutSuccess -> mainThreadExecutor.run(() -> handlePayoutDeliveryOutcome(
                                            player,
                                            session,
                                            operationId,
                                            slotIndex,
                                            payload,
                                            payoutService,
                                            deliveryToken,
                                            payoutSuccess)));
                        }),
                        Runnable::run)
                .exceptionally(error -> {
                    mainThreadExecutor.run(() -> {
                        if (activeSession(player, session)) {
                            player.sendMessage(Component.text("Failed to deliver withdrawn item.", NamedTextColor.RED));
                            refreshSession(player, session);
                        }
                    });
                    return null;
                });
    }

    private void handlePayoutDeliveryOutcome(
            Player player,
            Session session,
            UUID operationId,
            int slotIndex,
            OpaqueItemPayload payload,
            GuildStorageServiceImpl payoutService,
            UUID deliveryToken,
            boolean payoutSuccess) {
        if (payoutSuccess) {
            CompletableFuture.supplyAsync(
                            () -> {
                                if (!payoutService.isWithdrawPayoutDeliveryClaimActive(operationId, deliveryToken)) {
                                    return StorageResult.<Void>failure(
                                            StorageResult.Status.CONFLICT, "Withdraw payout delivery handoff mismatch");
                                }
                                return payoutService.confirmWithdrawPayoutDelivered(operationId, deliveryToken);
                            },
                            sqlExecutor)
                    .handle((confirmResult, error) -> {
                        boolean needsUnknown = error != null
                                || confirmResult == null
                                || !confirmResult.isSuccess();
                        if (needsUnknown
                                && payoutService.isWithdrawPayoutDeliveryClaimActive(operationId, deliveryToken)) {
                            payoutService.markWithdrawPayoutDeliveryUnknown(operationId, deliveryToken);
                        }
                        return null;
                    })
                    .thenRunAsync(
                            () -> mainThreadExecutor.run(() -> {
                                if (activeSession(player, session)) {
                                    refreshSession(player, session);
                                }
                            }),
                            Runnable::run);
            return;
        }
        CompletableFuture.supplyAsync(
                        () -> {
                            if (!payoutService.isWithdrawPayoutDeliveryClaimActive(operationId, deliveryToken)) {
                                return StorageResult.<Void>success(null);
                            }
                            return payoutService.cancelWithdrawPayoutDelivery(operationId, deliveryToken);
                        },
                        sqlExecutor)
                .thenAcceptAsync(
                        ignored -> mainThreadExecutor.run(() ->
                                restoreWithdrawPayout(player, session, operationId, slotIndex, payload)),
                        Runnable::run);
    }

    private void restoreWithdrawPayout(
            Player player,
            Session session,
            UUID operationId,
            int slotIndex,
            OpaqueItemPayload payload) {
        if (!(storageService instanceof GuildStorageServiceImpl impl)) {
            if (activeSession(player, session)) {
                player.sendMessage(Component.text("Not enough inventory space.", NamedTextColor.RED));
                refreshSession(player, session);
            }
            return;
        }
        CompletableFuture.supplyAsync(
                        () -> impl.compensateWithdrawPayout(
                                operationId,
                                player.getUniqueId(),
                                session.guildId(),
                                session.tabId(),
                                slotIndex,
                                payload,
                                session.facility().id()),
                        sqlExecutor)
                .thenAcceptAsync(
                        restoreResult -> mainThreadExecutor.run(() -> {
                            if (activeSession(player, session)) {
                                if (!restoreResult.isSuccess()) {
                                    player.sendMessage(Component.text(
                                            "Failed to restore withdrawn item to storage.", NamedTextColor.RED));
                                } else {
                                    player.sendMessage(Component.text(
                                            "Not enough inventory space.", NamedTextColor.RED));
                                }
                                refreshSession(player, session);
                            }
                        }),
                        Runnable::run);
    }

    private void restoreDepositItem(Player player, ItemStack item, UUID operationId) {
        if (!(storageService instanceof GuildStorageServiceImpl impl)) {
            restoreDepositItemDirect(player, item);
            return;
        }
        Runnable restoreWithClaim = () -> {
            try {
                CompletableFuture.supplyAsync(() -> impl.beginDepositRestorationDelivery(operationId), sqlExecutor)
                        .thenAcceptAsync(
                                claimResult -> mainThreadExecutor.run(() -> {
                                    if (!claimResult.isSuccess()) {
                                        restoreDepositItemDirect(player, item);
                                        return;
                                    }
                                    UUID handoffToken = claimResult.value().orElseThrow().handoffToken();
                                    restoreDepositItemWithHandoff(player, item, impl, operationId, handoffToken);
                                }),
                                Runnable::run)
                        .exceptionally(error -> {
                            mainThreadExecutor.run(() -> restoreDepositItemDirect(player, item));
                            return null;
                        });
            } catch (Throwable error) {
                mainThreadExecutor.run(() -> restoreDepositItemDirect(player, item));
            }
        };
        mainThreadExecutor.run(restoreWithClaim);
    }

    private void restoreDepositItemDirect(Player player, ItemStack item) {
        ItemStack current = player.getItemOnCursor();
        if (current == null || current.getType() == Material.AIR) {
            player.setItemOnCursor(item.clone());
            return;
        }
        inventoryCoordinator.giveItem(player.getUniqueId(), item.clone(), success -> {});
    }

    private void restoreDepositItemWithHandoff(
            Player player,
            ItemStack item,
            GuildStorageServiceImpl impl,
            UUID operationId,
            UUID handoffToken) {
        ItemStack current = player.getItemOnCursor();
        if (current == null || current.getType() == Material.AIR) {
            player.setItemOnCursor(item.clone());
            acknowledgeDepositRestoration(impl, operationId, handoffToken);
            return;
        }
        inventoryCoordinator.giveItem(player.getUniqueId(), item.clone(), success -> {
            if (!Boolean.TRUE.equals(success)) {
                try {
                    CompletableFuture.runAsync(
                            () -> impl.cancelDepositRestorationDelivery(operationId, handoffToken),
                            sqlExecutor);
                } catch (Throwable ignored) {
                    // Leave restoration obligation pending for reconciliation.
                }
                return;
            }
            acknowledgeDepositRestoration(impl, operationId, handoffToken);
        });
    }

    private void acknowledgeDepositRestoration(
            GuildStorageServiceImpl impl, UUID operationId, UUID handoffToken) {
        try {
            CompletableFuture.supplyAsync(
                            () -> impl.acknowledgeDepositRestoration(operationId, handoffToken),
                            sqlExecutor)
                    .exceptionally(error -> null);
        } catch (Throwable ignored) {
            // Leave restoration obligation pending for reconciliation.
        }
    }


    private boolean activeSession(Player player, Session session) {
        Session active = sessions.get(player.getUniqueId());
        return active != null && active == session;
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

    record Session(
            UUID playerId, SettlementFacility facility, String guildId, String tabId, Inventory inventory) {}

    private record OpenState(boolean success, String tabId, Map<Integer, StorageSlot> slots, String errorMessage) {
        static OpenState success(String tabId, Map<Integer, StorageSlot> slots) {
            return new OpenState(true, tabId, slots, null);
        }

        static OpenState failure(String errorMessage) {
            return new OpenState(false, null, Map.of(), errorMessage);
        }
    }
}
