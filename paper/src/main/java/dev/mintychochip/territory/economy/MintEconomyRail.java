package dev.mintychochip.territory.economy;

import dev.mintychochip.mint.api.id.AccountId;
import dev.mintychochip.mint.api.id.CurrencyId;
import dev.mintychochip.mint.api.id.IdempotencyKey;
import dev.mintychochip.mint.api.id.NamespaceId;
import dev.mintychochip.mint.api.ledger.Posting;
import dev.mintychochip.mint.api.ledger.TransactionReceipt;
import dev.mintychochip.mint.api.ledger.TransactionRequest;
import dev.mintychochip.mint.api.money.Money;
import dev.mintychochip.mint.api.result.Committed;
import dev.mintychochip.mint.api.result.OperationOutcome;
import dev.mintychochip.mint.api.result.Rejected;
import dev.mintychochip.mint.api.result.RejectionCode;
import dev.mintychochip.mint.api.service.MintClientLease;
import dev.mintychochip.territory.economy.AsyncSettlementResult.Status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/** Mint-backed asynchronous economy rail for player and guild accounts. */
public final class MintEconomyRail implements dev.mintychochip.guilds.services.MintTransferPort {
    private final MintClientLease lease;
    private final CurrencyId currency;
    private final int scale;
    private final Logger logger;

    public MintEconomyRail(MintClientLease lease, CurrencyId currency, int scale, Logger logger) {
        this.lease = Objects.requireNonNull(lease, "lease");
        this.currency = Objects.requireNonNull(currency, "currency");
        if (scale < 0) throw new IllegalArgumentException("scale must not be negative");
        this.scale = scale;
        this.logger = logger == null ? Logger.getLogger(MintEconomyRail.class.getName()) : logger;
    }

    public static AccountId guildAccount(String guildId) {
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guildId must not be blank");
        return AccountId.of(NamespaceId.parse("guild:" + guildId));
    }

    public static AccountId playerAccount(UUID playerId) {
        return AccountId.player(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public CompletionStage<MintOperationResult> openAccount(UUID playerId, String guildId) {
        AccountId player = playerAccount(playerId);
        AccountId guild = guildAccount(guildId);
        return lease.accounts().ensure(player)
                .thenCompose(ignored -> lease.accounts().ensure(guild))
                .thenApply(ignored -> new MintOperationResult(MintOperationResult.Status.COMMITTED, null,
                        java.util.Optional.empty(), java.util.Optional.empty()))
                .exceptionally(this::unavailable);
    }

    @Override
    public CompletionStage<MintOperationResult> balance(String guildId) {
        AccountId account = guildAccount(guildId);
        return lease.accounts().ensure(account)
                .thenCompose(ignored -> lease.ledger().balance(account, currency))
                .thenApply(snapshot -> new MintOperationResult(MintOperationResult.Status.COMMITTED,
                        snapshot.total(), java.util.Optional.empty(), java.util.Optional.empty()))
                .exceptionally(this::unavailable);
    }

    @Override
    public CompletionStage<MintOperationResult> deposit(UUID playerId, String guildId, BigDecimal amount, String key) {
        return transfer(playerId, guildId, amount, key, "deposit");
    }

    @Override
    public CompletionStage<MintOperationResult> withdraw(UUID playerId, String guildId, BigDecimal amount, String key) {
        return transfer(playerId, guildId, amount, key, "withdraw");
    }

    public CompletionStage<MintOperationResult> creditTax(UUID payerId, String guildId, BigDecimal amount, String key) {
        return transfer(payerId, guildId, amount, key, "tax");
    }

    private CompletionStage<MintOperationResult> transfer(UUID playerId, String guildId, BigDecimal rawAmount,
                                                           String key, String direction) {
        Objects.requireNonNull(playerId, "playerId");
        if (guildId == null || guildId.isBlank()) throw new IllegalArgumentException("guildId must not be blank");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank");
        BigDecimal amount = canonicalAmount(rawAmount);
        AccountId player = playerAccount(playerId);
        AccountId guild = guildAccount(guildId);
        AccountId source = direction.equals("withdraw") ? guild : player;
        AccountId destination = source.equals(player) ? guild : player;
        return lease.accounts().ensure(source)
                .thenCompose(ignored -> lease.accounts().ensure(destination))
                .thenCompose(ignored -> lease.ledger().transact(new TransactionRequest(
                        new IdempotencyKey(key),
                        List.of(new Posting(source, new Money(currency, amount.negate())),
                                new Posting(destination, new Money(currency, amount))),
                        "azoth.guild-bank.transfer",
                        Map.of("guild", guildId, "direction", direction, "idempotency-key", key))))
                .thenApply(this::mapOutcome)
                .exceptionally(this::unavailable);
    }

    private BigDecimal canonicalAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        BigDecimal result = amount.setScale(scale, RoundingMode.HALF_UP);
        if (result.signum() <= 0) throw new IllegalArgumentException("amount rounds to zero");
        return result;
    }

    private MintOperationResult mapOutcome(OperationOutcome<TransactionReceipt> outcome) {
        if (outcome instanceof Committed<TransactionReceipt> committed) {
            return new MintOperationResult(MintOperationResult.Status.COMMITTED, null, null,
                    committed.value().transactionId().toString());
        }
        if (outcome instanceof Rejected<TransactionReceipt> rejected) {
            var rejection = rejected.rejection();
            var status = rejection.code() == RejectionCode.INSUFFICIENT_AVAILABLE
                    ? MintOperationResult.Status.INSUFFICIENT_FUNDS : MintOperationResult.Status.REJECTED;
            return new MintOperationResult(status, null, rejection.code().name(), null);
        }
        return new MintOperationResult(MintOperationResult.Status.UNAVAILABLE, null, "UNKNOWN_OUTCOME", null);
    }

    private MintOperationResult unavailable(Throwable error) {
        logger.fine(() -> "Mint operation unavailable: " + error);
        return new MintOperationResult(MintOperationResult.Status.UNAVAILABLE, null,
                error.getClass().getSimpleName(), null);
    }

    private static AsyncSettlementResult.Status mapStatus(MintOperationResult.Status status) {
        return switch (status) {
            case COMMITTED -> AsyncSettlementResult.Status.COMMITTED;
            case INSUFFICIENT_FUNDS -> AsyncSettlementResult.Status.INSUFFICIENT_FUNDS;
            case UNAVAILABLE -> AsyncSettlementResult.Status.UNAVAILABLE;
            case REJECTED -> AsyncSettlementResult.Status.REJECTED;
        };
    }
}
