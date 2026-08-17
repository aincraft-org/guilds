package dev.mintychochip.territory.building;

import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.FacilityType;
import dev.mintychochip.territory.model.SettlementFacility;
import dev.mintychochip.territory.model.Territory;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TradingPostInteractEventTest {
    @Test
    void exposesResolvedContextAndCancellation() {
        Player player = mock(Player.class);
        SettlementFacility facility = new SettlementFacility(
                "market", "Market", "t1", FacilityType.TRADING_POST, "world", 5, 64, 5);
        Territory territory = new Territory("t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))))
                .withGoverningGuild("guild-1");
        TradingPostInteractEvent event = new TradingPostInteractEvent(player, facility, territory);

        assertEquals(player, event.player());
        assertEquals(facility, event.facility());
        assertEquals(territory, event.territory());
        assertEquals("guild-1", event.governingGuildId().orElseThrow());
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        assertEquals(TradingPostInteractEvent.getHandlerList(), event.getHandlers());
    }
}
