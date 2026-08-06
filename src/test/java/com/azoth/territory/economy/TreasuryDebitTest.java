package com.azoth.territory.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TreasuryDebitTest {

    private static EconomyResponse ok(double amount) {
        return new EconomyResponse(amount, amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private static EconomyResponse fail() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "failed");
    }

    @Test
    void simulationDebitReducesActiveTreasury() {
        SimulationTreasury treasury = new SimulationTreasury().credit("t1", 10.0);

        assertEquals(TreasuryDebitStatus.DEBITED,
                treasury.debitTreasury("t1", 4.0).status());
        assertEquals(6.0, treasury.activeBalanceOf("t1"), 1e-9);
    }

    @Test
    void simulationDebitNeverGoesNegative() {
        SimulationTreasury treasury = new SimulationTreasury().credit("t1", 10.0);

        assertEquals(TreasuryDebitStatus.INSUFFICIENT_FUNDS,
                treasury.debitTreasury("t1", 11.0).status());
        assertEquals(10.0, treasury.activeBalanceOf("t1"), 1e-9);
    }

    @Test
    void invalidDebitAmountIsReported() {
        SimulationTreasury treasury = new SimulationTreasury();

        assertEquals(TreasuryDebitStatus.INVALID_AMOUNT,
                treasury.debitTreasury("t1", 0.0).status());
        assertEquals(TreasuryDebitStatus.INVALID_AMOUNT,
                treasury.debitTreasury("t1", Double.NaN).status());
    }

    @Test
    void vaultDebitUsesOnlyTerritoryBank() {
        Economy economy = mock(Economy.class);
        when(economy.hasBankSupport()).thenReturn(true);
        when(economy.bankBalance("terr")).thenReturn(ok(20.0));
        when(economy.bankHas("terr", 4.0)).thenReturn(ok(20.0));
        when(economy.bankWithdraw("terr", 4.0)).thenReturn(ok(16.0));
        VaultTreasury treasury = new VaultTreasury(economy, id -> mock(OfflinePlayer.class));

        assertEquals(TreasuryDebitStatus.DEBITED,
                treasury.debitTreasury("terr", 4.0).status());
        verify(economy).bankHas("terr", 4.0);
        verify(economy).bankWithdraw("terr", 4.0);
        verify(economy, never()).withdrawPlayer(anyString(), anyDouble());
    }

    @Test
    void vaultDebitStopsWhenTreasuryCannotCoverAmount() {
        Economy economy = mock(Economy.class);
        when(economy.hasBankSupport()).thenReturn(true);
        when(economy.bankBalance("terr")).thenReturn(ok(2.0));
        when(economy.bankHas("terr", 4.0)).thenReturn(fail());
        VaultTreasury treasury = new VaultTreasury(economy, id -> mock(OfflinePlayer.class));

        assertEquals(TreasuryDebitStatus.INSUFFICIENT_FUNDS,
                treasury.debitTreasury("terr", 4.0).status());
        verify(economy, never()).bankWithdraw(eq("terr"), eq(4.0));
    }

    @Test
    void missingVaultIsUnavailable() {
        VaultTreasury treasury = new VaultTreasury(null, id -> mock(OfflinePlayer.class));

        assertEquals(TreasuryDebitStatus.VAULT_UNAVAILABLE,
                treasury.debitTreasury("terr", 4.0).status());
    }
}
