package org.aincraft.guilds.territory.listener;

import org.aincraft.guilds.territory.model.ZoneType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerritoryTransitionTitleFormatterTest {
    @Test
    void enteringWilderness_usesTerritoryAsSubtitle() {
        TerritoryTransitionTitleFormatter.Title title = TerritoryTransitionTitleFormatter.enter(
                Optional.of("Everfall"), ZoneType.WILDERNESS);

        assertEquals("Entering Wilderness", title.title());
        assertEquals("Everfall", title.subtitle());
    }

    @Test
    void enteringClaimable_usesZoneNameInTitle() {
        TerritoryTransitionTitleFormatter.Title title = TerritoryTransitionTitleFormatter.enter(
                Optional.of("Everfall"), ZoneType.CLAIMABLE);

        assertEquals("Entering Claimable", title.title());
        assertEquals("Everfall", title.subtitle());
    }

    @Test
    void leavingTerritory_usesWildernessDestination() {
        TerritoryTransitionTitleFormatter.Title title = TerritoryTransitionTitleFormatter.leave();

        assertEquals("Leaving Territory", title.title());
        assertEquals("Wilderness", title.subtitle());
    }

    @Test
    void sameChunkMovementStillUsesDestinationTransitionTitle() {
        // Territory transitions are resolved from block coordinates, not chunk changes.
        TerritoryTransitionTitleFormatter.Title title = TerritoryTransitionTitleFormatter.enter(
                Optional.of("Everfall"), ZoneType.WILDERNESS);

        assertEquals("Entering Wilderness", title.title());
        assertEquals("Everfall", title.subtitle());
    }
}
