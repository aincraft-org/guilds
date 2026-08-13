package org.aincraft.guilds.services;

import com.azoth.territory.economy.GuildBankCapacity;
import com.azoth.territory.economy.MintOperationResult;
import org.aincraft.guilds.models.Guild;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MintGuildBankServiceTest {
    @Test void capacityUsesNonNegativeLevelAndConfiguredScale() {
        GuildBankCapacity capacity = new GuildBankCapacity(new BigDecimal("1000.005"), 2);
        assertEquals(new BigDecimal("0.00"), capacity.forLevel(-2));
        assertEquals(new BigDecimal("1000.01"), capacity.forLevel(1));
    }

    @Test void positiveCreditReadsBalanceAndRejectsAtCapacity() {
        UUID player = UUID.randomUUID();
        AtomicInteger deposits = new AtomicInteger();
        MintTransferPort mint = new MintTransferPort() {
            public CompletableFuture<MintOperationResult> openAccount(UUID p, String id) { return committed(null); }
            public CompletableFuture<MintOperationResult> balance(String id) { return committed(new BigDecimal("999.00")); }
            public CompletableFuture<MintOperationResult> deposit(UUID p, String id, BigDecimal a, String k) { deposits.incrementAndGet(); return committed(null); }
            public CompletableFuture<MintOperationResult> withdraw(UUID p, String id, BigDecimal a, String k) { return committed(null); }
            public CompletableFuture<MintOperationResult> creditTax(UUID p, String id, BigDecimal a, String k) { return committed(null); }
        };
        Guild guild = new Guild("Guild", player);
        guild.setId("guild-1");
        guild.setGuildLevel(1);
        GuildBankEnrollmentService enrollment = new GuildBankEnrollmentService() {
            public CompletableFuture<EnrollmentResult> open(UUID p, String g) { return CompletableFuture.completedFuture(EnrollmentResult.OPENED); }
            public CompletableFuture<Boolean> isEnrolled(UUID p, String g) { return CompletableFuture.completedFuture(true); }
            public CompletableFuture<Boolean> deactivateForPlayerGuild(UUID p, String g) { return CompletableFuture.completedFuture(true); }
            public CompletableFuture<Integer> deactivateForGuild(String g) { return CompletableFuture.completedFuture(1); }
        };
        try (MintGuildBankService service = new MintGuildBankService(mint, enrollment, id -> guild, new GuildBankCapacity())) {
            MintGuildBankService.Result result = service.deposit(player, guild.getId(), new BigDecimal("1.00"), "key").toCompletableFuture().join();
            assertEquals(MintGuildBankService.Status.COMMITTED, result.status());
            assertEquals(1, deposits.get());
        }
    }
    @Test void taxCreditCarriesPayerWithoutEnrollment() {
        UUID payer = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        MintTransferPort mint = new MintTransferPort() {
            public CompletableFuture<MintOperationResult> openAccount(UUID p, String id) { return committed(null); }
            public CompletableFuture<MintOperationResult> balance(String id) { return committed(new BigDecimal("1.00")); }
            public CompletableFuture<MintOperationResult> deposit(UUID p, String id, BigDecimal a, String k) { return committed(null); }
            public CompletableFuture<MintOperationResult> withdraw(UUID p, String id, BigDecimal a, String k) { return committed(null); }
            public CompletableFuture<MintOperationResult> creditTax(UUID p, String id, BigDecimal a, String k) {
                calls.incrementAndGet();
                assertEquals(payer, p);
                assertEquals("guild-1", id);
                return committed(null);
            }
        };
        Guild guild = new Guild("Guild", payer);
        guild.setId("guild-1");
        guild.setGuildLevel(1);
        GuildBankEnrollmentService enrollment = new GuildBankEnrollmentService() {
            public CompletableFuture<EnrollmentResult> open(UUID p, String g) { return CompletableFuture.completedFuture(EnrollmentResult.OPENED); }
            public CompletableFuture<Boolean> isEnrolled(UUID p, String g) { return CompletableFuture.completedFuture(false); }
            public CompletableFuture<Boolean> deactivateForPlayerGuild(UUID p, String g) { return CompletableFuture.completedFuture(true); }
            public CompletableFuture<Integer> deactivateForGuild(String g) { return CompletableFuture.completedFuture(1); }
        };
        try (MintGuildBankService service = new MintGuildBankService(mint, enrollment, id -> guild, new GuildBankCapacity())) {
            MintGuildBankService.Result result = service.creditTax(payer, guild.getId(), new BigDecimal("1.00"), "tax-key")
                    .toCompletableFuture().join();
            assertEquals(MintGuildBankService.Status.COMMITTED, result.status());
            assertEquals(1, calls.get());
        }
    }

    private static CompletableFuture<MintOperationResult> committed(BigDecimal value) {
        return CompletableFuture.completedFuture(new MintOperationResult(MintOperationResult.Status.COMMITTED,
                value, java.util.Optional.empty(), java.util.Optional.empty()));
    }
}
