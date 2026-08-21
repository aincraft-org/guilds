package org.aincraft.guilds.storage;

import org.aincraft.guilds.listeners.GuildBankVillagerListener;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.GuildBankEnrollmentService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.MintGuildBankService;
import org.aincraft.guilds.services.MintTransferPort;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.territory.building.BuildingConfig;
import org.aincraft.guilds.territory.building.BuildingConfigLoader;
import org.aincraft.guilds.territory.economy.GuildBankCapacity;
import org.aincraft.guilds.territory.economy.MintOperationResult;
import org.aincraft.guilds.territory.model.FacilityType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageRegressionTest {
    @Test
    void storageIsSupportedWhenConfigured() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("buildings.storage.anchor-materials", List.of("BARREL", "CHEST"));

        BuildingConfig config = BuildingConfigLoader.from(yaml);

        assertTrue(config.supports(FacilityType.STORAGE));
    }

    @Test
    void storageIsCurrentlyUnsupportedByDefault() {
        assertFalse(BuildingConfigLoader.from(new YamlConfiguration()).supports(FacilityType.STORAGE));
    }

    @Test
    void taggedVillagerBypassesTerritoryBounds() {
        UUID playerId = UUID.randomUUID();
        String guildId = "guild-1";
        Player player = mock(Player.class);
        Villager villager = mock(Villager.class);
        JavaPlugin plugin = mock(JavaPlugin.class);
        Resident resident = new Resident(playerId, "player");
        resident.setGuild(guildId);
        Guild guild = new Guild("Guild", playerId);
        guild.setId(guildId);
        AtomicReference<UUID> openedPlayer = new AtomicReference<>();
        AtomicReference<String> openedGuild = new AtomicReference<>();
        CompletableFuture<Void> mintOpened = new CompletableFuture<>();

        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        });
        when(player.getUniqueId()).thenReturn(playerId);
        when(villager.getScoreboardTags()).thenReturn(java.util.Set.of("GUILD_BANK"));
        ResidentService residents = mock(ResidentService.class);
        when(residents.getResident(playerId)).thenReturn(Optional.of(resident));
        GuildService guilds = mock(GuildService.class);
        when(guilds.getGuild(guildId)).thenReturn(Optional.of(guild));
        MintTransferPort mint = new MintTransferPort() {
            public CompletableFuture<MintOperationResult> openAccount(UUID player, String id) {
                openedPlayer.set(player);
                openedGuild.set(id);
                mintOpened.complete(null);
                return committed();
            }
            public CompletableFuture<MintOperationResult> balance(String id) { return committed(); }
            public CompletableFuture<MintOperationResult> deposit(UUID p, String id, java.math.BigDecimal a, String k) { return committed(); }
            public CompletableFuture<MintOperationResult> withdraw(UUID p, String id, java.math.BigDecimal a, String k) { return committed(); }
            public CompletableFuture<MintOperationResult> creditTax(UUID p, String id, java.math.BigDecimal a, String k) { return committed(); }
        };
        GuildBankEnrollmentService enrollment = new GuildBankEnrollmentService() {
            public CompletableFuture<EnrollmentResult> open(UUID p, String g) { return CompletableFuture.completedFuture(EnrollmentResult.OPENED); }
            public CompletableFuture<Boolean> isEnrolled(UUID p, String g) { return CompletableFuture.completedFuture(true); }
            public CompletableFuture<Boolean> deactivateForPlayerGuild(UUID p, String g) { return CompletableFuture.completedFuture(true); }
            public CompletableFuture<Integer> deactivateForGuild(String g) { return CompletableFuture.completedFuture(1); }
        };

        try (MintGuildBankService bank = new MintGuildBankService(mint, enrollment, id -> guild, new GuildBankCapacity())) {
            GuildBankVillagerListener listener = new GuildBankVillagerListener(plugin, guilds, residents, bank, "GUILD_BANK");
            listener.onPlayerInteractEntity(new PlayerInteractEntityEvent(player, villager));
            assertDoesNotThrow(() -> mintOpened.get(5, TimeUnit.SECONDS));
            assertEquals(playerId, openedPlayer.get());
            assertEquals(guildId, openedGuild.get());
        }
        verify(villager, never()).getLocation();
        verify(player, never()).getLocation();
    }

    private static CompletableFuture<MintOperationResult> committed() {
        return CompletableFuture.completedFuture(new MintOperationResult(
                MintOperationResult.Status.COMMITTED, null, Optional.empty(), Optional.empty()));
    }
}
