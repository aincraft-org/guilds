package com.azoth.territory.building;

import com.azoth.territory.model.FacilityType;
import com.azoth.territory.model.SettlementFacility;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SafeLandingResolverTest {
    @Test
    void prefersPassableSpaceAboveAnchor() {
        Server server = mock(Server.class);
        World world = mock(World.class);
        when(server.getWorld("world")).thenReturn(world);
        Block feet = passable(false);
        Block head = passable(false);
        Block support = mock(Block.class);
        when(support.isPassable()).thenReturn(false);
        when(support.isLiquid()).thenReturn(false);
        when(world.getBlockAt(5, 65, 5)).thenReturn(feet);
        when(world.getBlockAt(5, 66, 5)).thenReturn(head);
        when(world.getBlockAt(5, 64, 5)).thenReturn(support);
        SettlementFacility destination = new SettlementFacility(
                "south", "South", "t1", FacilityType.WAYSTONE, "world", 5, 64, 5);

        var result = new SafeLandingResolver(server).find(destination).orElseThrow();

        assertEquals(5.5, result.getX());
        assertEquals(65.0, result.getY());
        assertEquals(5.5, result.getZ());
    }

    private static Block passable(boolean liquid) {
        Block block = mock(Block.class);
        when(block.isPassable()).thenReturn(true);
        when(block.isLiquid()).thenReturn(liquid);
        return block;
    }
}
