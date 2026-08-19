package org.aincraft.guilds.territory.web;

import org.aincraft.guilds.territory.standing.StandingBar;
import org.aincraft.guilds.territory.standing.StandingService;
import org.aincraft.guilds.territory.standing.StandingTier;
import org.aincraft.guilds.territory.standing.TerritoryStandingState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingWebTest {

    private static Supplier<Optional<StandingService>> supplierOf(StandingService service) {
        return () -> Optional.of(service);
    }

    private static TerritoryApiHandler handlerWith(StandingService standing) {
        // 8-arg constructor: config, proxy, registry, json, store, influence, standing, log
        return new TerritoryApiHandler(
                null, null, null, null, null,
                Optional::empty, supplierOf(standing), Logger.getLogger("test"));
    }

    @Test
    void standingJson_serializesAllStates() {
        StandingService standing = fakeStanding(new TerritoryStandingState(
                "everfall", "everfall-town",
                List.of(new StandingBar("everfall-town", 200.0))));
        String json = handlerWith(standing).standingJson();
        assertTrue(json.contains("\"everfall\""));
        assertTrue(json.contains("\"everfall-town\""));
        assertTrue(json.contains("200.0"));
    }

    @Test
    void emptyStanding_returnsEmptyArray() {
        StandingService standing = fakeStanding(null);
        String json = handlerWith(standing).standingJson();
        assertTrue(json.contains("\"standing\":[]"));
    }

    private static StandingService fakeStanding(TerritoryStandingState state) {
        return new StandingService() {
            @Override public Optional<TerritoryStandingState> standing(String territoryId) {
                return state == null ? Optional.empty() : Optional.of(state);
            }
            @Override public List<TerritoryStandingState> all() {
                return state == null ? List.of() : List.of(state);
            }
            @Override public double harvestMultiplierFor(String t, String g) { return 1.0; }
            @Override public double influenceMultiplierFor(String g) { return 1.0; }
            @Override public Optional<StandingTier> tierFor(String t, String g) { return Optional.empty(); }
            @Override public boolean adminSet(String t, String g, double v) { return false; }
            @Override public boolean adminReset(String t) { return false; }
        };
    }
}
