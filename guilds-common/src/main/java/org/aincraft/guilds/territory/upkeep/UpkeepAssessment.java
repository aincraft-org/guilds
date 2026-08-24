package org.aincraft.guilds.territory.upkeep;

/** Deterministic cost breakdown for one territory at a point in time. */
public record UpkeepAssessment(
        String territoryId,
        double amount,
        int footprintUnits,
        int facilityCount,
        int developmentLevel
) {
    public UpkeepAssessment {
        if (territoryId == null || territoryId.isBlank()) {
            throw new IllegalArgumentException("territoryId must not be blank");
        }
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("amount must be finite and non-negative");
        }
        if (footprintUnits < 0 || facilityCount < 0 || developmentLevel < 0) {
            throw new IllegalArgumentException("upkeep dimensions must be non-negative");
        }
    }
}
