package org.aincraft.guilds.storage.gui;

import net.kyori.adventure.text.Component;
import org.aincraft.guilds.storage.persist.SqlGuildStorageStore;
import org.aincraft.guilds.storage.service.PlayerInventoryCoordinator;
import org.aincraft.guilds.storage.service.PayoutDeliveryHandoff;
import org.aincraft.guilds.storage.service.StorageResult;
import org.aincraft.guilds.storage.service.impl.GuildStorageServiceImpl;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.aincraft.guilds.territory.storage.StorageTab;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuildStorageGUITest {
    @Mock
    private JavaPlugin plugin;
    @Mock
    private GuildStorageServiceImpl storageService;
    @Mock
    private PlayerInventoryCoordinator inventoryCoordinator;
    @Mock
    private Player player;
    @Mock
    private Inventory inventory;

    private MockedStatic<Bukkit> bukkit;
    private GuildStorageGUI gui;
    private UUID playerId;
    private SettlementFacility facility;
    private String guildId;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        guildId = "guild-1";
        facility = new SettlementFacility(
                "storage-1", "Vault", "territory-1", FacilityType.STORAGE, "world", 1, 64, 1);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(() -> Bukkit.createInventory(any(GuildStorageGUI.class), eq(54), any(Component.class)))
                .thenReturn(inventory);

        when(player.getUniqueId()).thenReturn(playerId);
        when(storageService.getTabs(playerId, guildId)).thenReturn(StorageResult.success(List.of(
                new StorageTab(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, "General", 0, 54, true))));
        when(storageService.getSlots(playerId, guildId, SqlGuildStorageStore.DEFAULT_TAB_ID))
                .thenReturn(StorageResult.success(Map.of()));

        lenient().when(storageService.beginWithdrawPayoutDelivery(any(UUID.class)))
                .thenReturn(StorageResult.success(new PayoutDeliveryHandoff(UUID.randomUUID())));

        gui = new GuildStorageGUI(plugin, storageService, inventoryCoordinator, Runnable::run);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void openCreatesSessionAndInventory() {
        gui.open(player, facility, guildId);

        verify(inventory).clear();
        verify(player).openInventory(inventory);
        assertSame(facility, gui.sessionFor(playerId).facility());
    }

    @Test
    void depositUsesOperationIdAndCompensatesOnFailure() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND);
        when(stack.clone()).thenReturn(stack);
        when(stack.serializeAsBytes()).thenReturn(new byte[] {1, 2, 3});
        when(inventory.getItem(4)).thenReturn(null);

        AtomicReference<UUID> operationId = new AtomicReference<>();
        AtomicReference<Runnable> compensation = new AtomicReference<>();
        when(storageService.depositWithCompensation(
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(4),
                any(OpaqueItemPayload.class),
                eq(facility.id()),
                any(UUID.class),
                any()))
                .thenAnswer(invocation -> {
                    operationId.set(invocation.getArgument(6));
                    compensation.set(invocation.getArgument(7));
                    return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, "forced failure");
                });

        gui.open(player, facility, guildId);
        gui.onInventoryClick(click(4, stack));

        verify(player).setItemOnCursor(null);
        verify(storageService).depositWithCompensation(
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(4),
                any(OpaqueItemPayload.class),
                eq(facility.id()),
                any(UUID.class),
                any());
        assertNotNull(operationId.get());
        compensation.get().run();
        verify(player).setItemOnCursor(stack);
        verify(inventoryCoordinator, never()).giveItem(any(), any(), any());
    }

    @Test
    void withdrawUsesOperationIdAndGivesItemOnSuccess() {
        ItemStack stored = mock(ItemStack.class);
        ItemStack decoded = mock(ItemStack.class);
        when(stored.getType()).thenReturn(Material.EMERALD);
        byte[] bytes = new byte[] {4, 5, 6};
        OpaqueItemPayload payload = new OpaqueItemPayload(
                "paper:v1", sha256(bytes), Base64.getEncoder().encodeToString(bytes));
        when(inventory.getItem(2)).thenReturn(stored);
        when(storageService.withdrawWithCompensation(
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(2),
                eq(facility.id()),
                any(UUID.class),
                isNull()))
                .thenReturn(StorageResult.success(payload));

        try (MockedStatic<ItemStack> itemStacks = mockStatic(ItemStack.class)) {
            itemStacks.when(() -> ItemStack.deserializeBytes(bytes)).thenReturn(decoded);

            gui.open(player, facility, guildId);
            gui.onInventoryClick(click(2, null));
        }

        verify(storageService).withdrawWithCompensation(
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(2),
                eq(facility.id()),
                any(UUID.class),
                isNull());
        verify(inventoryCoordinator).giveItem(eq(playerId), eq(decoded), any());
    }

    @Test
    void closeClearsSessionWithoutMutatingCursor() {
        gui.open(player, facility, guildId);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        when(view.getPlayer()).thenReturn(player);
        InventoryCloseEvent closeEvent = new InventoryCloseEvent(view);
        when(inventory.getHolder()).thenReturn(gui);

        gui.onInventoryClose(closeEvent);

        assertNull(gui.sessionFor(playerId));
        verify(player, never()).setItemOnCursor(any());
    }
    @Test
    void withdrawDoesNotRegisterPrematureInventoryCompensation() {
        ItemStack stored = mock(ItemStack.class);
        when(stored.getType()).thenReturn(Material.EMERALD);
        when(inventory.getItem(2)).thenReturn(stored);
        when(storageService.withdrawWithCompensation(
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(2),
                eq(facility.id()),
                any(UUID.class),
                isNull()))
                .thenReturn(StorageResult.failure(StorageResult.Status.CONFLICT, "forced failure"));

        gui.open(player, facility, guildId);
        gui.onInventoryClick(click(2, null));

        verify(storageService).withdrawWithCompensation(
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(2),
                eq(facility.id()),
                any(UUID.class),
                isNull());
        verify(inventoryCoordinator, never()).removeMatching(any(), any(), any());
    }

    @Test
    void withdrawRestoresToStorageWhenPayoutFails() {
        ItemStack stored = mock(ItemStack.class);
        ItemStack decoded = mock(ItemStack.class);
        when(stored.getType()).thenReturn(Material.EMERALD);
        byte[] bytes = new byte[] {4, 5, 6};
        OpaqueItemPayload payload = new OpaqueItemPayload(
                "paper:v1", sha256(bytes), Base64.getEncoder().encodeToString(bytes));
        when(inventory.getItem(2)).thenReturn(stored);
        when(storageService.withdrawWithCompensation(
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(2),
                eq(facility.id()),
                any(UUID.class),
                isNull()))
                .thenReturn(StorageResult.success(payload));
        when(storageService.compensateWithdrawPayout(
                any(UUID.class),
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(2),
                eq(payload),
                eq(facility.id())))
                .thenReturn(StorageResult.success(null));
        org.mockito.Mockito.doAnswer(invocation -> {
                    java.util.function.Consumer<Boolean> callback = invocation.getArgument(2);
                    callback.accept(false);
                    return null;
                })
                .when(inventoryCoordinator)
                .giveItem(eq(playerId), eq(decoded), any());

        try (MockedStatic<ItemStack> itemStacks = mockStatic(ItemStack.class)) {
            itemStacks.when(() -> ItemStack.deserializeBytes(bytes)).thenReturn(decoded);
            gui.open(player, facility, guildId);
            gui.onInventoryClick(click(2, null));
        }

        verify(storageService).compensateWithdrawPayout(
                any(UUID.class),
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(2),
                eq(payload),
                eq(facility.id()));
        verify(storageService).cancelWithdrawPayoutDelivery(any(UUID.class), any(UUID.class));
    }


    @Test
    void depositPreCommitFailureRestoresCursorOnce() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND);
        when(stack.clone()).thenReturn(stack);
        when(stack.serializeAsBytes()).thenReturn(new byte[] {1, 2, 3});
        when(inventory.getItem(4)).thenReturn(null);
        AtomicInteger compensationRuns = new AtomicInteger();
        when(storageService.depositWithCompensation(
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(4),
                any(OpaqueItemPayload.class),
                eq(facility.id()),
                any(UUID.class),
                any())).thenAnswer(invocation -> {
            Runnable compensation = invocation.getArgument(7);
            compensation.run();
            compensationRuns.incrementAndGet();
            return StorageResult.failure(StorageResult.Status.PERMISSION_DENIED, "forced failure");
        });

        gui.open(player, facility, guildId);
        gui.onInventoryClick(click(4, stack));

        assertEquals(1, compensationRuns.get());
        verify(player).setItemOnCursor(stack);
        verify(inventoryCoordinator, never()).giveItem(any(), any(), any());
    }

    @Test
    void depositUncheckedFailureRestoresCursorExactlyOnce() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND);
        when(stack.clone()).thenReturn(stack);
        when(stack.serializeAsBytes()).thenReturn(new byte[] {1, 2, 3});
        when(inventory.getItem(4)).thenReturn(null);
        when(storageService.depositWithCompensation(
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(4),
                any(OpaqueItemPayload.class),
                eq(facility.id()),
                any(UUID.class),
                any())).thenThrow(new IllegalStateException("simulated deposit failure"));

        gui.open(player, facility, guildId);
        gui.onInventoryClick(click(4, stack));

        verify(player).setItemOnCursor(null);
        verify(player).setItemOnCursor(stack);
        verify(inventoryCoordinator, never()).giveItem(any(), any(), any());
    }

    @Test
    void depositExecutorSubmitFailureRestoresCursorExactlyOnce() throws Exception {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND);
        when(stack.clone()).thenReturn(stack);
        when(stack.serializeAsBytes()).thenReturn(new byte[] {1, 2, 3});
        when(inventory.getItem(4)).thenReturn(null);
        gui.open(player, facility, guildId);
        GuildStorageGUI failingDepositGui = new GuildStorageGUI(
                plugin,
                storageService,
                inventoryCoordinator,
                Runnable::run,
                command -> {
                    throw new IllegalStateException("simulated executor submit failure");
                });
        java.lang.reflect.Field sessionsField = GuildStorageGUI.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        sessionsField.set(failingDepositGui, sessionsField.get(gui));

        failingDepositGui.onInventoryClick(click(4, stack));

        verify(player).setItemOnCursor(null);
        verify(player).setItemOnCursor(stack);
        verify(storageService, never()).depositWithCompensation(
                any(), any(), any(), anyInt(), any(), any(), any(), any());
        verify(inventoryCoordinator, never()).giveItem(any(), any(), any());
    }

    @Test
    void depositFailureRestoresItemWhenSessionInactive() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND);
        when(stack.clone()).thenReturn(stack);
        when(stack.serializeAsBytes()).thenReturn(new byte[] {1, 2, 3});
        when(inventory.getItem(4)).thenReturn(null);
        AtomicReference<Runnable> compensation = new AtomicReference<>();
        when(storageService.depositWithCompensation(
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(4),
                any(OpaqueItemPayload.class),
                eq(facility.id()),
                any(UUID.class),
                any())).thenAnswer(invocation -> {
            compensation.set(invocation.getArgument(7));
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, "forced failure");
        });

        gui.open(player, facility, guildId);
        gui.onInventoryClick(click(4, stack));
        sessionsRemove(playerId);
        compensation.get().run();

        verify(player).setItemOnCursor(stack);
    }

    @Test
    void depositFailureAcknowledgesRestorationOnlyAfterSuccessfulRestore() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND);
        when(stack.clone()).thenReturn(stack);
        when(stack.serializeAsBytes()).thenReturn(new byte[] {1, 2, 3});
        when(inventory.getItem(4)).thenReturn(null);
        when(player.getItemOnCursor()).thenReturn(null);
        AtomicReference<Runnable> compensation = new AtomicReference<>();
        when(storageService.depositWithCompensation(
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(4),
                any(OpaqueItemPayload.class),
                eq(facility.id()),
                any(UUID.class),
                any())).thenAnswer(invocation -> {
            compensation.set(invocation.getArgument(7));
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, "forced failure");
        });

        gui.open(player, facility, guildId);
        gui.onInventoryClick(click(4, stack));
        compensation.get().run();

        verify(player).setItemOnCursor(stack);
        verify(storageService).acknowledgeDepositRestoration(any(UUID.class));
    }

    @Test
    void withdrawMarksUnknownWhenConfirmFailsAfterSuccessfulGive() {
        ItemStack stored = mock(ItemStack.class);
        ItemStack decoded = mock(ItemStack.class);
        when(stored.getType()).thenReturn(Material.EMERALD);
        byte[] bytes = new byte[] {4, 5, 6};
        OpaqueItemPayload payload = new OpaqueItemPayload(
                "paper:v1", sha256(bytes), Base64.getEncoder().encodeToString(bytes));
        UUID deliveryToken = UUID.randomUUID();
        when(inventory.getItem(2)).thenReturn(stored);
        when(storageService.withdrawWithCompensation(
                eq(playerId),
                eq(guildId),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(2),
                eq(facility.id()),
                any(UUID.class),
                isNull()))
                .thenReturn(StorageResult.success(payload));
        when(storageService.beginWithdrawPayoutDelivery(any(UUID.class)))
                .thenReturn(StorageResult.success(new PayoutDeliveryHandoff(deliveryToken)));
        when(storageService.confirmWithdrawPayoutDelivered(any(UUID.class), eq(deliveryToken)))
                .thenReturn(StorageResult.failure(StorageResult.Status.CONFLICT, "marker failed"));
        org.mockito.Mockito.doAnswer(invocation -> {
                    java.util.function.Consumer<Boolean> callback = invocation.getArgument(2);
                    callback.accept(true);
                    return null;
                })
                .when(inventoryCoordinator)
                .giveItem(eq(playerId), eq(decoded), any());

        try (MockedStatic<ItemStack> itemStacks = mockStatic(ItemStack.class)) {
            itemStacks.when(() -> ItemStack.deserializeBytes(bytes)).thenReturn(decoded);

            gui.open(player, facility, guildId);
            gui.onInventoryClick(click(2, null));
        }

        verify(storageService).markWithdrawPayoutDeliveryUnknown(any(UUID.class), eq(deliveryToken));
    }


    private void sessionsRemove(UUID playerId) {
        try {
            java.lang.reflect.Field field = GuildStorageGUI.class.getDeclaredField("sessions");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<UUID, ?> sessions = (java.util.Map<UUID, ?>) field.get(gui);
            sessions.remove(playerId);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }


    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private InventoryClickEvent click(int slot, ItemStack cursor) {
        when(inventory.getHolder()).thenReturn(gui);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(slot);
        when(event.getCursor()).thenReturn(cursor);
        return event;
    }
}
