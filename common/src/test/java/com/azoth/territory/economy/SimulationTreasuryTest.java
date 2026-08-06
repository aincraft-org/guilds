package com.azoth.territory.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tax math and the non-monetary simulation treasury ledger. */
class SimulationTreasuryTest {

    @Test
    void taxIsPercentOfGross() {
        assertEquals(1.5, TaxCalculator.tax(10.0, 15.0), 1e-9);
        assertEquals(0.0, TaxCalculator.tax(100.0, 0.0), 1e-9);
    }

    @Test
    void taxRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> TaxCalculator.tax(-1.0, 15.0));
        assertThrows(IllegalArgumentException.class, () -> TaxCalculator.tax(Double.NaN, 15.0));
        assertThrows(IllegalArgumentException.class, () -> TaxCalculator.tax(10.0, -5.0));
        assertThrows(IllegalArgumentException.class, () -> TaxCalculator.tax(10.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void startsEmpty() {
        SimulationTreasury t = new SimulationTreasury();
        assertEquals(0.0, t.balanceOf("terr"), 1e-9);
        assertTrue(t.balances().isEmpty());
    }

    @Test
    void creditDebitAreCopyOnWrite() {
        SimulationTreasury t = new SimulationTreasury();
        SimulationTreasury credited = t.credit("terr", 10.0);
        assertEquals(0.0, t.balanceOf("terr"), 1e-9);
        assertEquals(10.0, credited.balanceOf("terr"), 1e-9);
        SimulationTreasury debited = credited.debit("terr", 4.0);
        assertEquals(6.0, debited.balanceOf("terr"), 1e-9);
        assertEquals(10.0, credited.balanceOf("terr"), 1e-9);
    }

    @Test
    void debitNeverGoesNegative() {
        SimulationTreasury t = new SimulationTreasury().credit("terr", 5.0);
        assertEquals(5.0, t.debit("terr", 99.0).balanceOf("terr"), 1e-9);
    }

    @Test
    void invalidAmountsRejected() {
        SimulationTreasury t = new SimulationTreasury();
        assertThrows(IllegalArgumentException.class, () -> t.credit("terr", 0.0));
        assertThrows(IllegalArgumentException.class, () -> t.credit("terr", -1.0));
        assertThrows(IllegalArgumentException.class, () -> t.debit("terr", 0.0));
        assertThrows(IllegalArgumentException.class, () -> t.debit("terr", Double.NaN));
    }
}
