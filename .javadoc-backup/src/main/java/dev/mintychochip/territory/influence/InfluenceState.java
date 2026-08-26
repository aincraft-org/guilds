package dev.mintychochip.territory.influence;

import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory influence state for all territories (engine-internal). */
final class InfluenceState {
    static final int VERSION = 1;
    final Map<String, TerritoryEntry> entries = new LinkedHashMap<>();
}
