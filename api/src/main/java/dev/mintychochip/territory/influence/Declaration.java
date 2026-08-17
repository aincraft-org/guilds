package dev.mintychochip.territory.influence;

/** An active takeover declaration (race is locked while present).
 *
 * @param guildId guild making the declaration
 * @param declaredAtEpochMs declaration start time in epoch milliseconds
 * @param flipAtEpochMs scheduled flip time in epoch milliseconds
 */
public record Declaration(String guildId, long declaredAtEpochMs, long flipAtEpochMs) {
    /** Validates the declaration components. */
    public Declaration {
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("guildId is required");
        }
    }
}
