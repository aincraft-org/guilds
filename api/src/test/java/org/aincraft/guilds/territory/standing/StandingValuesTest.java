package org.aincraft.guilds.territory.standing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StandingValuesTest {

    @Test
    void sources_areExactlyPvePvpBlockBreak() {
        assertEquals(List.of(
                StandingSource.PVP_KILL,
                StandingSource.PVE_KILL,
                StandingSource.BLOCK_BREAK
        ), List.of(StandingSource.values()));
    }

    @Test
    void bar_holdsGuildAndValue() {
        StandingBar bar = new StandingBar("g1", 12.5);
        assertEquals("g1", bar.guildId());
        assertEquals(12.5, bar.value(), 0.001);
    }

    @Test
    void state_exposesTerritoryOwnerAndBars() {
        TerritoryStandingState state = new TerritoryStandingState(
                "everfall", "everfall-town",
                List.of(new StandingBar("everfall-town", 200.0)));
        assertEquals("everfall", state.territoryId());
        assertEquals("everfall-town", state.ownerGuildId());
        assertEquals(1, state.bars().size());
    }

    @Test
    void tier_validatesMultipliers() {
        assertThrows(IllegalArgumentException.class,
                () -> new StandingTier(1, 0, 0.5, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new StandingTier(1, 0, 1.0, 0.9));
        assertThrows(IllegalArgumentException.class,
                () -> new StandingTier(0, 0, 1.0, 1.0));
        assertEquals(2, new StandingTier(2, 100, 1.2, 1.1).level());
    }
}
