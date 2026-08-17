package com.azoth.territory.standing;

/**
 * Standing value of one guild on one territory.
 *
 * @param guildId guild identifier
 * @param value standing value
 */
public record StandingBar(String guildId, double value) {
}
