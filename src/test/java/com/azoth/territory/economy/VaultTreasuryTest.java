package com.azoth.territory.economy;

import com.azoth.territory.economy.PaymentRail.SettlementStatus;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** VaultTreasury: withdraw-first ordering, refund-on-deposit-failure, reconciliation flag. */
class VaultTreasuryTest {

    private static final UUID P = UUID.randomUUID();
    private static final OfflinePlayer PLAYER = mock(OfflinePlayer.class);

    private static EconomyResponse ok(double amount) {
        return new EconomyResponse(amount, amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private static EconomyResponse fail() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "failed");
    }

    private static VaultTreasury treasury(Economy e, boolean bankExists) {
        when(e.hasBankSupport()).thenReturn(true);
        when(e.bankBalance(eq("terr"))).thenReturn(bankExists ? ok(0) : fail());
        return new VaultTreasury(e, id -> PLAYER);
    }

    @Test
    void settledWhenBothLegsSucceed() {
        Economy e = mock(Economy.class);
        when(e.hasAccount(PLAYER)).thenReturn(true);
        when(e.has(PLAYER, 10.0)).thenReturn(true);
        when(e.withdrawPlayer(PLAYER, 10.0)).thenReturn(ok(10.0));
        when(e.bankDeposit("terr", 10.0)).thenReturn(ok(10.0));
        VaultTreasury v = treasury(e, true);

        assertEquals(SettlementStatus.SETTLED, v.settle(P, "terr", 10.0).status());
        verify(e).withdrawPlayer(PLAYER, 10.0);
        verify(e).bankDeposit("terr", 10.0);
        verify(e).bankBalance("terr");
    }

    @Test
    void insufficientFundsMovesNothing() {
        Economy e = mock(Economy.class);
        when(e.hasAccount(PLAYER)).thenReturn(true);
        when(e.has(PLAYER, 10.0)).thenReturn(false);
        VaultTreasury v = treasury(e, true);

        assertEquals(SettlementStatus.INSUFFICIENT_FUNDS, v.settle(P, "terr", 10.0).status());
        verify(e).has(PLAYER, 10.0);
        verify(e, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
        verify(e, never()).bankDeposit(any(), anyDouble());
    }

    @Test
    void withdrawFailureMovesNothing() {
        Economy e = mock(Economy.class);
        when(e.hasAccount(PLAYER)).thenReturn(true);
        when(e.has(PLAYER, 10.0)).thenReturn(true);
        when(e.withdrawPlayer(PLAYER, 10.0)).thenReturn(fail());
        VaultTreasury v = treasury(e, true);

        assertEquals(SettlementStatus.PAYER_UNAVAILABLE, v.settle(P, "terr", 10.0).status());
        verify(e, never()).bankDeposit(any(), anyDouble());
    }

    @Test
    void depositFailureTriggersRefundNetZero() {
        Economy e = mock(Economy.class);
        when(e.hasAccount(PLAYER)).thenReturn(true);
        when(e.has(PLAYER, 10.0)).thenReturn(true);
        when(e.withdrawPlayer(PLAYER, 10.0)).thenReturn(ok(10.0));
        when(e.bankDeposit("terr", 10.0)).thenReturn(fail());
        when(e.depositPlayer(PLAYER, 10.0)).thenReturn(ok(10.0));
        VaultTreasury v = treasury(e, true);

        assertEquals(SettlementStatus.COMPENSATED_FAILURE, v.settle(P, "terr", 10.0).status());
        verify(e).withdrawPlayer(PLAYER, 10.0);
        verify(e).bankDeposit("terr", 10.0);
        verify(e).depositPlayer(PLAYER, 10.0);
    }

    @Test
    void refundFailureFlagsReconciliation() {
        Economy e = mock(Economy.class);
        when(e.hasAccount(PLAYER)).thenReturn(true);
        when(e.has(PLAYER, 10.0)).thenReturn(true);
        when(e.withdrawPlayer(PLAYER, 10.0)).thenReturn(ok(10.0));
        when(e.bankDeposit("terr", 10.0)).thenReturn(fail());
        when(e.depositPlayer(PLAYER, 10.0)).thenReturn(fail());
        VaultTreasury v = treasury(e, true);

        assertEquals(SettlementStatus.RECONCILIATION_REQUIRED, v.settle(P, "terr", 10.0).status());
        verify(e).withdrawPlayer(PLAYER, 10.0);
        verify(e).bankDeposit("terr", 10.0);
        verify(e).depositPlayer(PLAYER, 10.0);
    }

    @Test
    void bankNotProvisionedIsVaultUnavailable() {
        Economy e = mock(Economy.class);
        VaultTreasury v = treasury(e, false);
        assertEquals(SettlementStatus.VAULT_UNAVAILABLE, v.settle(P, "terr", 10.0).status());
        verify(e, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    void provisionTerritoriesCreatesMissingBanks() {
        Economy e = mock(Economy.class);
        when(e.hasBankSupport()).thenReturn(true);
        when(e.bankBalance("terr")).thenReturn(fail());
        when(e.createBank("terr", "AzothTerritory-Service")).thenReturn(ok(0));
        VaultTreasury v = new VaultTreasury(e, id -> PLAYER);

        assertEquals(0, v.provisionTerritories(List.of("terr")));
        verify(e).createBank("terr", "AzothTerritory-Service");
    }

    @Test
    void provisionFailureCountsUnprovisioned() {
        Economy e = mock(Economy.class);
        when(e.hasBankSupport()).thenReturn(true);
        when(e.bankBalance("terr")).thenReturn(fail());
        when(e.createBank(eq("terr"), anyString())).thenReturn(fail());
        VaultTreasury v = new VaultTreasury(e, id -> PLAYER);

        assertEquals(1, v.provisionTerritories(List.of("terr")));
    }

    @Test
    void unavailableWhenVaultEconomyMissing() {
        VaultTreasury v = new VaultTreasury(null, id -> PLAYER);
        assertFalse(v.available());
        assertEquals(SettlementStatus.VAULT_UNAVAILABLE, v.settle(P, "terr", 10.0).status());
    }
}
