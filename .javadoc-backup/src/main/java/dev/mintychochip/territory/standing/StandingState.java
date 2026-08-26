package dev.mintychochip.territory.standing;

import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory standing state for all territories (engine-internal). */
final class StandingState {
    static final int VERSION = 1;
    final Map<String, StandingEntry> entries = new LinkedHashMap<>();
}
