package org.aincraft.towny.services;

import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.aincraft.towny.services.impl.EconomyServiceImpl;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EconomyServiceImplTest {

    private EconomyService service;

    @Mock
    private TownyPlugin plugin;

    @Mock
    private Server server;

    @Mock
    private PluginManager pluginManager;
    @Mock
    private ServicesManager servicesManager;

    @Mock
    private RegisteredServiceProvider<Economy> economyRegistration;

    @Mock
    private Economy vaultEconomy;

    @BeforeEach
    void setup() {
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EconomyServiceImplTest"));
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("Vault")).thenReturn(null);

        service = new EconomyServiceImpl(plugin, mock(DatabaseManager.class));
    }

    @Test
    void unavailableWithoutVault() {
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void townBalanceIsZeroWithoutVault() {
        assertThat(service.getTownBalance("town")).isZero();
        assertThat(service.townHas("town", 1.0)).isFalse();
    }

    @Test
    void townOperationsDoNotPersistWithoutVault() {
        DatabaseManager databaseManager = mock(DatabaseManager.class);
        service = new EconomyServiceImpl(plugin, databaseManager);

        service.depositTown("town", 100.0);
        service.withdrawTown("town", 25.0);

        assertThat(service.getTownBalance("town")).isZero();
        verifyNoInteractions(databaseManager);
    }

    @Test
    void playerOperationsAreUnavailableWithoutVault() {
        UUID playerId = UUID.randomUUID();

        service.depositPlayer(playerId, 100.0);
        service.withdrawPlayer(playerId, 25.0);

        assertThat(service.getPlayerBalance(playerId)).isZero();
        assertThat(service.has(playerId, 1.0)).isFalse();
    }

    @Test
    void invalidTownAmountsAreIgnored() {
        service.depositTown("town", 0.0);
        service.depositTown("town", -1.0);
        service.withdrawTown("town", 0.0);
        service.withdrawTown("town", -1.0);

        assertThat(service.getTownBalance("town")).isZero();
    }

    @Test
    void formatHasSafeFallbackWithoutVault() {
        assertThat(service.format(123.45)).isEqualTo("$123.45");
    }

    @Test
    void failedTownDepositIsNotLoggedAsSuccessful() {
        DatabaseManager databaseManager = mock(DatabaseManager.class);
        when(pluginManager.getPlugin("Vault")).thenReturn(mock(Plugin.class));
        when(server.getServicesManager()).thenReturn(servicesManager);
        when(servicesManager.getRegistration(Economy.class)).thenReturn(economyRegistration);
        when(economyRegistration.getProvider()).thenReturn(vaultEconomy);
        when(vaultEconomy.getName()).thenReturn("test");
        when(vaultEconomy.bankDeposit("town", 100.0)).thenReturn(
                new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "failed"));

        service = new EconomyServiceImpl(plugin, databaseManager);
        service.depositTown("town", 100.0);

        verify(vaultEconomy).bankDeposit("town", 100.0);
        verifyNoInteractions(databaseManager);
    }
}