package org.aincraft.guilds.services;

import org.aincraft.guilds.services.impl.GuildBankEnrollmentServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildBankEnrollmentServiceTest {
    void implementation_exposesAsyncEnrollmentLifecycleContract() throws NoSuchMethodException {
        assertTrue(GuildBankEnrollmentService.class.isAssignableFrom(GuildBankEnrollmentServiceImpl.class));
        assertTrue(java.util.concurrent.CompletionStage.class.isAssignableFrom(
                GuildBankEnrollmentService.class.getMethod("open", java.util.UUID.class, String.class).getReturnType()));
        assertTrue(java.util.concurrent.CompletionStage.class.isAssignableFrom(
                GuildBankEnrollmentService.class.getMethod("isEnrolled", java.util.UUID.class, String.class).getReturnType()));
    }
}
