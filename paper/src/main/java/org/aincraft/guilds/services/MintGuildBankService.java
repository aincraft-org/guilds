package org.aincraft.guilds.services;

import com.azoth.territory.economy.GuildBankCapacity;
import com.azoth.territory.economy.MintOperationResult;
import org.aincraft.guilds.models.Guild;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Serializes asynchronous guild-bank operations and enforces guild capacity. */
public final class MintGuildBankService implements AutoCloseable {
    public enum Status { COMMITTED, CAPACITY_EXCEEDED, UNAUTHORIZED, UNAVAILABLE, INSUFFICIENT_FUNDS, REJECTED }

    public record Result(Status status, BigDecimal value, String diagnosticCode, String receiptIdentifier) {
        static Result from(MintOperationResult result) {
            return new Result(switch (result.status()) {
                case COMMITTED -> Status.COMMITTED;
                case INSUFFICIENT_FUNDS -> Status.INSUFFICIENT_FUNDS;
                case UNAVAILABLE -> Status.UNAVAILABLE;
                case REJECTED -> Status.REJECTED;
            }, result.value(), result.diagnosticCode().orElse(null), result.receiptIdentifier().orElse(null));
        }
        static Result unavailable(String code) { return new Result(Status.UNAVAILABLE, null, code, null); }
    }

    private final MintTransferPort mint;
    private final GuildBankEnrollmentService enrollment;
    private final GuildResolver guilds;
    private final GuildBankCapacity capacity;
    private final long timeoutMillis;
    private final ScheduledExecutorService timer;
    private final ConcurrentMap<String, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface GuildResolver { Guild resolve(String guildId); }

    public MintGuildBankService(MintTransferPort mint, GuildBankEnrollmentService enrollment,
                                GuildResolver guilds, GuildBankCapacity capacity, long timeoutMillis) {
        this.mint = Objects.requireNonNull(mint);
        this.enrollment = Objects.requireNonNull(enrollment);
        this.guilds = Objects.requireNonNull(guilds);
        this.capacity = Objects.requireNonNull(capacity);
        if (timeoutMillis <= 0) throw new IllegalArgumentException("timeoutMillis must be positive");
        this.timeoutMillis = timeoutMillis;
        this.timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "guild-bank-timeouts"); t.setDaemon(true); return t;
        });
    }

    public MintGuildBankService(MintTransferPort mint, GuildBankEnrollmentService enrollment,
                                GuildResolver guilds, GuildBankCapacity capacity) {
        this(mint, enrollment, guilds, capacity, 5000);
    }

    public CompletionStage<Result> openAccount(UUID player, String guildId) {
        return enqueue(guildId, () -> enrollment.open(player, guildId).thenCompose(r -> {
            if (r != GuildBankEnrollmentService.EnrollmentResult.OPENED
                    && r != GuildBankEnrollmentService.EnrollmentResult.ALREADY_OPEN) {
                return CompletableFuture.completedFuture(new Result(
                        r == GuildBankEnrollmentService.EnrollmentResult.NOT_CURRENT_MEMBER
                                ? Status.UNAUTHORIZED : Status.REJECTED, null, r.name(), null));
            }
            return call(() -> mint.openAccount(player, guildId)).thenApply(Result::from);
        }));
    }

    public CompletionStage<Result> balance(UUID player, String guildId) {
        return enqueue(guildId, () -> authorized(player, guildId, false).thenCompose(ok -> {
            if (!ok) return CompletableFuture.completedFuture(new Result(Status.UNAUTHORIZED, null, "NOT_ENROLLED", null));
            return call(() -> mint.balance(guildId)).thenApply(Result::from);
        }));
    }

    public CompletionStage<Result> deposit(UUID player, String guildId, BigDecimal amount, String key) {
        return credit(player, guildId, amount, key, false);
    }

    public CompletionStage<Result> creditTax(UUID payerUuid, String guildId, BigDecimal amount, String key) {
        return credit(payerUuid, guildId, amount, key, true);
    }

    public CompletionStage<Result> creditTax(String guildId, BigDecimal amount, String key) {
        return creditTax(null, guildId, amount, key);
    }

    private CompletionStage<Result> credit(UUID player, String guildId, BigDecimal amount, String key, boolean tax) {
        return enqueue(guildId, () -> authorized(player, guildId, tax).thenCompose(ok -> {
            if (!ok) return CompletableFuture.completedFuture(new Result(Status.UNAUTHORIZED, null, "NOT_ENROLLED", null));
            return capacityCheck(guildId, amount).thenCompose(allowed -> {
                if (!allowed) return CompletableFuture.completedFuture(new Result(Status.CAPACITY_EXCEEDED, null, "CAPACITY", null));
                return call(() -> tax ? mint.creditTax(player, guildId, amount, key) : mint.deposit(player, guildId, amount, key))
                        .thenApply(Result::from);
            });
        }));
    }

    public CompletionStage<Result> withdraw(UUID player, String guildId, BigDecimal amount, String key) {
        return enqueue(guildId, () -> authorized(player, guildId, false).thenCompose(ok -> {
            if (!ok) return CompletableFuture.completedFuture(new Result(Status.UNAUTHORIZED, null, "NOT_ENROLLED", null));
            return call(() -> mint.withdraw(player, guildId, amount, key)).thenApply(Result::from);
        }));
    }


    private CompletionStage<Boolean> authorized(UUID player, String guildId, boolean tax) {
        return tax ? CompletableFuture.completedFuture(true) : enrollment.isEnrolled(player, guildId);
    }

    private CompletionStage<Boolean> capacityCheck(String guildId, BigDecimal amount) {
        Guild guild = guilds.resolve(guildId);
        if (guild == null) return CompletableFuture.completedFuture(false);
        return call(() -> mint.balance(guild.getId())).thenApply(r -> r.status() == MintOperationResult.Status.COMMITTED
                && r.value() != null && r.value().add(amount).compareTo(capacity.forLevel(guild.getGuildLevel())) <= 0);
    }
    private <T> CompletionStage<T> call(Supplier<CompletionStage<T>> operation) {
        try {
            return operation.get();
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    private CompletionStage<Result> enqueue(String guildId, Supplier<CompletionStage<Result>> operation) {
        if (guildId == null || guildId.isBlank()) return CompletableFuture.completedFuture(Result.unavailable("INVALID_GUILD"));
        CompletableFuture<Result> returned = new CompletableFuture<>();
        tails.compute(guildId, (id, tail) -> {
            CompletableFuture<Void> prior = tail == null ? CompletableFuture.completedFuture(null) : tail;
            CompletableFuture<Void> barrier = prior.handle((v, e) -> (Void) null).thenCompose(v -> {
                CompletionStage<Result> stage;
                try {
                    stage = operation.get();
                } catch (Throwable t) {
                    returned.complete(Result.unavailable(t.getClass().getSimpleName()));
                    return CompletableFuture.completedFuture((Void) null);
                }
                CompletableFuture<Result> operationResult = new CompletableFuture<>();
                timer.schedule(() -> operationResult.complete(Result.unavailable("TIMEOUT")),
                        timeoutMillis, TimeUnit.MILLISECONDS);
                operationResult.whenComplete((timeoutResult, timeoutError) -> {
                    if (timeoutError == null && timeoutResult != null) returned.complete(timeoutResult);
                });
                stage.whenComplete((value, error) -> {
                    Result result = error == null ? value : Result.unavailable(error.getClass().getSimpleName());
                    operationResult.complete(result);
                    returned.complete(result);
                });
                return stage.handle((v2, e2) -> (Void) null);
            }).toCompletableFuture();
            barrier.whenComplete((v, e) -> tails.remove(id, barrier));
            return barrier;
        });
        return returned;
    }

    @Override public void close() { timer.shutdownNow(); }
}
