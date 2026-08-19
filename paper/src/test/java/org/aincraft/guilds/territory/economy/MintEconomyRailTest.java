package org.aincraft.guilds.territory.economy;

import dev.mintychochip.mint.api.id.AccountId;
import dev.mintychochip.mint.api.id.CurrencyId;
import dev.mintychochip.mint.api.id.IdempotencyKey;
import dev.mintychochip.mint.api.id.NamespaceId;
import dev.mintychochip.mint.api.ledger.BalanceSnapshot;
import dev.mintychochip.mint.api.ledger.TransactionReceipt;
import dev.mintychochip.mint.api.ledger.TransactionRequest;
import dev.mintychochip.mint.api.result.Committed;
import dev.mintychochip.mint.api.result.Rejected;
import dev.mintychochip.mint.api.result.Rejection;
import dev.mintychochip.mint.api.result.RejectionCode;
import dev.mintychochip.mint.api.service.AccountService;
import dev.mintychochip.mint.api.service.LedgerService;
import dev.mintychochip.mint.api.service.MintClientLease;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MintEconomyRailTest {
    private static final CurrencyId CURRENCY = CurrencyId.parse("azoth:coins");
    private final MintClientLease lease = mock(MintClientLease.class);
    private final AccountService accounts = mock(AccountService.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final UUID player = UUID.randomUUID();

    MintEconomyRailTest() {
        when(lease.accounts()).thenReturn(accounts);
        when(lease.ledger()).thenReturn(ledger);
        when(accounts.ensure(org.mockito.ArgumentMatchers.any())).thenReturn(CompletableFuture.completedFuture(true));
    }

    @Test
    void accountIdsUsePlayerAndGuildNamespaces() {
        assertEquals(AccountId.player(player), MintEconomyRail.playerAccount(player));
        assertEquals(AccountId.of(NamespaceId.parse("guild:eldoria")), MintEconomyRail.guildAccount("eldoria"));
    }

    @Test
    void transferScalesAmountEnsuresBothAccountsAndUsesSignedAtomicPostings() {
        var receipt = new TransactionReceipt(UUID.randomUUID(), new IdempotencyKey("key-1"),
                dev.mintychochip.mint.api.ledger.TransactionKind.TRANSFER, List.of(), Instant.now(), Map.of());
        when(ledger.transact(org.mockito.ArgumentMatchers.any())).thenReturn(
                CompletableFuture.completedFuture(new Committed<>(receipt)));

        var result = new MintEconomyRail(lease, CURRENCY, 2, java.util.logging.Logger.getLogger("test"))
                .deposit(player, "eldoria", new BigDecimal("12.345"), "key-1").toCompletableFuture().join();

        assertEquals(MintOperationResult.Status.COMMITTED, result.status());
        var request = forClass(TransactionRequest.class);
        verify(ledger).transact(request.capture());
        var captured = request.getValue();
        assertEquals(new BigDecimal("12.35"), captured.postings().get(0).money().amount().negate());
        assertEquals(new BigDecimal("12.35"), captured.postings().get(1).money().amount());
        assertEquals("guilds.guild-bank.transfer", captured.reason());
        assertEquals("eldoria", captured.metadata().get("guild"));
        assertEquals("deposit", captured.metadata().get("direction"));
        verify(accounts).ensure(AccountId.player(player));
        verify(accounts).ensure(AccountId.of(NamespaceId.parse("guild:eldoria")));
    }

    @Test
    void mapsInsufficientFundsAndBalance() {
        when(ledger.transact(org.mockito.ArgumentMatchers.any())).thenReturn(CompletableFuture.completedFuture(
                new Rejected<>(new Rejection(RejectionCode.INSUFFICIENT_AVAILABLE, "short", Map.of()))));
        var rail = new MintEconomyRail(lease, CURRENCY, 2, java.util.logging.Logger.getLogger("test"));
        assertEquals(MintOperationResult.Status.INSUFFICIENT_FUNDS,
                rail.withdraw(player, "eldoria", BigDecimal.ONE, "key").toCompletableFuture().join().status());

        when(accounts.ensure(AccountId.of(NamespaceId.parse("guild:eldoria")))).thenReturn(CompletableFuture.completedFuture(true));
        when(ledger.balance(AccountId.of(NamespaceId.parse("guild:eldoria")), CURRENCY)).thenReturn(
                CompletableFuture.completedFuture(new BalanceSnapshot(AccountId.of(NamespaceId.parse("guild:eldoria")), CURRENCY,
                        new BigDecimal("4.20"), BigDecimal.ZERO, 1, Instant.now())));
        assertEquals(0, new BigDecimal("4.20").compareTo(rail.balance("eldoria").toCompletableFuture().join().value()));
    }
}
