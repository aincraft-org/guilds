package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.services.impl.GuildBankEnrollmentServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for guild bank enrollment service. */
class GuildBankEnrollmentServiceTest {
    /**
     * Performs the implementation exposes async enrollment lifecycle contract operation.
     * @throws NoSuchMethodException if an error occurs
     */
    void implementation_exposesAsyncEnrollmentLifecycleContract() throws NoSuchMethodException {
        assertTrue(GuildBankEnrollmentService.class.isAssignableFrom(GuildBankEnrollmentServiceImpl.class));
        assertTrue(java.util.concurrent.CompletionStage.class.isAssignableFrom(
                GuildBankEnrollmentService.class.getMethod("open", java.util.UUID.class, String.class).getReturnType()));
        assertTrue(java.util.concurrent.CompletionStage.class.isAssignableFrom(
                GuildBankEnrollmentService.class.getMethod("isEnrolled", java.util.UUID.class, String.class).getReturnType()));
    }
}
