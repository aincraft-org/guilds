package org.aincraft.guilds.territory.economy;

/** Pure tax math: tax = gross * ratePercent / 100. */
public final class TaxCalculator {
    private TaxCalculator() {
    }

    public static double tax(double grossAmount, double ratePercent) {
        if (!Double.isFinite(grossAmount) || grossAmount <= 0) {
            throw new IllegalArgumentException("grossAmount must be a positive finite number, got " + grossAmount);
        }
        if (!Double.isFinite(ratePercent) || ratePercent < 0) {
            throw new IllegalArgumentException("ratePercent must be non-negative and finite, got " + ratePercent);
        }
        return grossAmount * ratePercent / 100.0;
    }
}
