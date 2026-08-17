package dev.mintychochip.territory.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class AsyncTaxSettlementContractTest {
    @Test
    void exposesAsyncSettlementSignature() throws Exception {
        var method = AsyncTaxSettlement.class.getMethod(
                "settle", UUID.class, String.class, BigDecimal.class, String.class);
        assertEquals(CompletionStage.class, method.getReturnType());
        assertEquals(AsyncSettlementResult.class,
                java.lang.reflect.ParameterizedType.class.cast(method.getGenericReturnType())
                        .getActualTypeArguments()[0]);
    }

    @Test
    void rejectsInvalidRequiredInputs() {
        assertThrows(NullPointerException.class, () -> AsyncTaxSettlement.validate(null, "guild", BigDecimal.ONE, "key"));
        assertThrows(IllegalArgumentException.class, () -> AsyncTaxSettlement.validate(UUID.randomUUID(), " ", BigDecimal.ONE, "key"));
        assertThrows(IllegalArgumentException.class, () -> AsyncTaxSettlement.validate(UUID.randomUUID(), "guild", BigDecimal.ZERO, "key"));
        assertThrows(IllegalArgumentException.class, () -> AsyncTaxSettlement.validate(UUID.randomUUID(), "guild", BigDecimal.ONE, " "));
    }

    @Test
    void statusesAreStableAndResultsImmutable() {
        assertEquals(Set.of("COMMITTED", "INSUFFICIENT_FUNDS", "UNAVAILABLE", "REJECTED", "RECONCILIATION_REQUIRED"),
                Set.of(AsyncSettlementResult.Status.values()).stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        var result = new AsyncSettlementResult(AsyncSettlementResult.Status.COMMITTED, " ok ", "receipt");
        assertEquals("ok", result.diagnosticCode().orElseThrow());
        assertEquals("receipt", result.receiptIdentifier().orElseThrow());
        assertTrue(result.getClass().isRecord());
    }

    @Test
    void hasNoMintOrPaperDependencies() {
        assertTrue(java.util.Arrays.stream(AsyncTaxSettlement.class.getDeclaredMethods())
                .noneMatch(m -> m.toString().contains("org.bukkit") || m.toString().contains("org.bukkit.craftbukkit") || m.toString().contains("org.bukkit.plugin")));
    }
}
