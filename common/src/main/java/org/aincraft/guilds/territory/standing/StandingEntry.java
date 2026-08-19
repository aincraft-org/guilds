package org.aincraft.guilds.territory.standing;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable in-memory standing for one territory (engine-internal). */
final class StandingEntry {
    String ownerGuildId;
    final Map<String, Double> bars = new LinkedHashMap<>();
}
