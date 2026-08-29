package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.building.boat.BoatRouteCalculator;
import org.aincraft.guilds.territory.building.boat.BoatRouteResult;
import org.aincraft.guilds.territory.building.boat.BoatWaterMask;
import org.aincraft.guilds.territory.building.boat.BoatWaterSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoatRouteCalculatorTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void findsConnectedRouteAndReturnsOnlyScalarDistance() {
        BoatWaterMask.Cell start = new BoatWaterMask.Cell(0, 62, 0);
        BoatWaterMask.Cell middle = new BoatWaterMask.Cell(1, 62, 0);
        BoatWaterMask.Cell end = new BoatWaterMask.Cell(2, 62, 0);
        BoatWaterSnapshot snapshot = snapshot(start, middle, end);

        BoatRouteResult result = new BoatRouteCalculator().calculate(
                List.of(snapshot), start, end, 1, 16);

        assertEquals(BoatRouteResult.Status.CONNECTED, result.status());
        assertEquals(2.0, result.distance());
        assertTrue(result.isScalarOnly());
    }

    @Test
    void distinguishesDisconnectedRoute() {
        BoatWaterMask.Cell start = new BoatWaterMask.Cell(0, 62, 0);
        BoatWaterMask.Cell end = new BoatWaterMask.Cell(2, 62, 0);
        BoatRouteResult result = new BoatRouteCalculator().calculate(
                List.of(snapshot(start, end)), start, end, 1, 16);

        assertEquals(BoatRouteResult.Status.DISCONNECTED, result.status());
    }

    @Test
    void hardNodeBudgetReturnsPendingBeforeExceedingBudget() {
        BoatWaterMask.Cell start = new BoatWaterMask.Cell(0, 62, 0);
        BoatWaterMask.Cell middle = new BoatWaterMask.Cell(1, 62, 0);
        BoatWaterMask.Cell end = new BoatWaterMask.Cell(2, 62, 0);

        BoatRouteResult result = new BoatRouteCalculator().calculate(
                List.of(snapshot(start, middle, end)), start, end, 1, 1);

        assertEquals(BoatRouteResult.Status.PENDING, result.status());
    }

    @Test
    void invalidEndpointIsUnavailable() {
        BoatWaterMask.Cell start = new BoatWaterMask.Cell(0, 62, 0);
        BoatWaterMask.Cell missing = new BoatWaterMask.Cell(4, 62, 0);

        BoatRouteResult result = new BoatRouteCalculator().calculate(
                List.of(snapshot(start)), start, missing, 1, 16);

        assertEquals(BoatRouteResult.Status.UNAVAILABLE, result.status());
    }

    @Test
    void snapshotsDefensivelyCopyWaterCells() {
        BoatWaterMask.Cell cell = new BoatWaterMask.Cell(0, 62, 0);
        BoatWaterMask mask = new BoatWaterMask(0, 0, Set.of(cell));
        BoatWaterSnapshot snapshot = new BoatWaterSnapshot(WORLD, mask);

        assertThrows(UnsupportedOperationException.class,
                () -> mask.cells().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.clearSpaceCells().clear());
    }

    private static BoatWaterSnapshot snapshot(BoatWaterMask.Cell... cells) {
        BoatWaterMask mask = new BoatWaterMask(0, 0, Set.of(cells));
        return new BoatWaterSnapshot(WORLD, 0, 0, mask, Set.of(cells));
    }
}
