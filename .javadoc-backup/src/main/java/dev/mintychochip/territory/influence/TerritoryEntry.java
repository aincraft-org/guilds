package dev.mintychochip.territory.influence;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable in-memory race state for one territory (engine-internal). */
final class TerritoryEntry {
    String ownerGuildId;
    long cooldownUntilEpochMs;
    final Map<String, Double> bars = new LinkedHashMap<>();
    Declaration declaration;
    PendingFlip pendingFlip;
}
