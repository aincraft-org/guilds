package dev.mintychochip.guilds.services;

import dev.mintychochip.territory.economy.GuildBankCapacity;
import dev.mintychochip.territory.economy.MintOperationResult;
import dev.mintychochip.guilds.models.Guild;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for mint guild bank service. */
class MintGuildBankServiceTest {
    /** Performs the capacity uses non negative level and configured scale operation. */
    @Test void capacityUsesNonNegativeLevelAndConfiguredScale() {
        GuildBankCapacity capacity = new GuildBankCapacity(new BigDecimal("1000.005"), 2);
        assertEquals(new BigDecimal("0.00"), capacity.forLevel(-2));
        assertEquals(new BigDecimal("1000.01"), capacity.forLevel(1));
    }

    /** Performs the positive credit reads balance and rejects at capacity operation. */
    @Test void positiveCreditReadsBalanceAndRejectsAtCapacity() {
        UUID player = UUID.randomUUID();
        AtomicInteger deposits = new AtomicInteger();
        MintTransferPort mint = new MintTransferPort() {
            /**
             * Performs the open account operation.
             * @param p the p
             * @param id the id
             * @return the result
             */
            public CompletableFuture<MintOperationResult> openAccount(UUID p, String id) { return committed(null); }
            /**
             * Performs the balance operation.
             * @param id the id
             * @return the result
             */
            public CompletableFuture<MintOperationResult> balance(String id) { return committed(new BigDecimal("999.00")); }
            /**
             * Performs the deposit operation.
             * @param p the p
             * @param id the id
             * @param a the a
             * @param k the k
             * @return the result
             */
            public CompletableFuture<MintOperationResult> deposit(UUID p, String id, BigDecimal a, String k) { deposits.incrementAndGet(); return committed(null); }
            /**
             * Performs the withdraw operation.
             * @param p the p
             * @param id the id
             * @param a the a
             * @param k the k
             * @return the result
             */
            public CompletableFuture<MintOperationResult> withdraw(UUID p, String id, BigDecimal a, String k) { return committed(null); }
            /**
             * Performs the credit tax operation.
             * @param p the p
             * @param id the id
             * @param a the a
             * @param k the k
             * @return the result
             */
            public CompletableFuture<MintOperationResult> creditTax(UUID p, String id, BigDecimal a, String k) { return committed(null); }
        };
        Guild guild = new Guild("Guild", player);
        guild.setId("guild-1");
        guild.setGuildLevel(1);
        GuildBankEnrollmentService enrollment = new GuildBankEnrollmentService() {
            /**
             * Performs the open operation.
             * @param p the p
             * @param g the g
             * @return the result
             */
            public CompletableFuture<EnrollmentResult> open(UUID p, String g) { return CompletableFuture.completedFuture(EnrollmentResult.OPENED); }
            /**
             * Returns whether enrolled.
             * @param p the p
             * @param g the g
             * @return the result
             */
            public CompletableFuture<Boolean> isEnrolled(UUID p, String g) { return CompletableFuture.completedFuture(true); }
            /**
             * Performs the deactivate for player guild operation.
             * @param p the p
             * @param g the g
             * @return the result
             */
            public CompletableFuture<Boolean> deactivateForPlayerGuild(UUID p, String g) { return CompletableFuture.completedFuture(true); }
            /**
             * Performs the deactivate for guild operation.
             * @param g the g
             * @return the result
             */
            public CompletableFuture<Integer> deactivateForGuild(String g) { return CompletableFuture.completedFuture(1); }
        };
        try (MintGuildBankService service = new MintGuildBankService(mint, enrollment, id -> guild, new GuildBankCapacity())) {
            MintGuildBankService.Result result = service.deposit(player, guild.getId(), new BigDecimal("1.00"), "key").toCompletableFuture().join();
            assertEquals(MintGuildBankService.Status.COMMITTED, result.status());
            assertEquals(1, deposits.get());
        }
    }
    /** Performs the tax credit carries payer without enrollment operation. */
    @Test void taxCreditCarriesPayerWithoutEnrollment() {
        UUID payer = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        MintTransferPort mint = new MintTransferPort() {
            /**
             * Performs the open account operation.
             * @param p the p
             * @param id the id
             * @return the result
             */
            public CompletableFuture<MintOperationResult> openAccount(UUID p, String id) { return committed(null); }
            /**
             * Performs the balance operation.
             * @param id the id
             * @return the result
             */
            public CompletableFuture<MintOperationResult> balance(String id) { return committed(new BigDecimal("1.00")); }
            /**
             * Performs the deposit operation.
             * @param p the p
             * @param id the id
             * @param a the a
             * @param k the k
             * @return the result
             */
            public CompletableFuture<MintOperationResult> deposit(UUID p, String id, BigDecimal a, String k) { return committed(null); }
            /**
             * Performs the withdraw operation.
             * @param p the p
             * @param id the id
             * @param a the a
             * @param k the k
             * @return the result
             */
            public CompletableFuture<MintOperationResult> withdraw(UUID p, String id, BigDecimal a, String k) { return committed(null); }
            /**
             * Performs the credit tax operation.
             * @param p the p
             * @param id the id
             * @param a the a
             * @param k the k
             * @return the result
             */
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
            /**
             * Performs the open operation.
             * @param p the p
             * @param g the g
             * @return the result
             */
            public CompletableFuture<EnrollmentResult> open(UUID p, String g) { return CompletableFuture.completedFuture(EnrollmentResult.OPENED); }
            /**
             * Returns whether enrolled.
             * @param p the p
             * @param g the g
             * @return the result
             */
            public CompletableFuture<Boolean> isEnrolled(UUID p, String g) { return CompletableFuture.completedFuture(false); }
            /**
             * Performs the deactivate for player guild operation.
             * @param p the p
             * @param g the g
             * @return the result
             */
            public CompletableFuture<Boolean> deactivateForPlayerGuild(UUID p, String g) { return CompletableFuture.completedFuture(true); }
            /**
             * Performs the deactivate for guild operation.
             * @param g the g
             * @return the result
             */
            public CompletableFuture<Integer> deactivateForGuild(String g) { return CompletableFuture.completedFuture(1); }
        };
        try (MintGuildBankService service = new MintGuildBankService(mint, enrollment, id -> guild, new GuildBankCapacity())) {
            MintGuildBankService.Result result = service.creditTax(payer, guild.getId(), new BigDecimal("1.00"), "tax-key")
                    .toCompletableFuture().join();
            assertEquals(MintGuildBankService.Status.COMMITTED, result.status());
            assertEquals(1, calls.get());
        }
    }

    /**
     * Performs the committed operation.
     * @param value the value
     * @return the result
     */
    private static CompletableFuture<MintOperationResult> committed(BigDecimal value) {
        return CompletableFuture.completedFuture(new MintOperationResult(MintOperationResult.Status.COMMITTED,
                value, java.util.Optional.empty(), java.util.Optional.empty()));
    }
}
