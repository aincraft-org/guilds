package org.aincraft.guilds.territory.invasion;

import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.services.PlotService;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvasionListenerTest {
    @Test void untaggedExplosionPreservesBlocks() {
        InvasionListener listener = listener(mock(InvasionRuntime.class), mock(InvasionEngine.class), mock(PlotService.class));
        Entity mob = mock(Entity.class); Block block = mock(Block.class); World world = mock(World.class);
        when(mob.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(block.getType()).thenReturn(Material.DIRT); when(block.getWorld()).thenReturn(world);
        EntityExplodeEvent event = mock(EntityExplodeEvent.class); when(event.getEntity()).thenReturn(mob); when(event.blockList()).thenReturn(new java.util.ArrayList<>(List.of(block)));
        listener.onExplode(event);
        verify(event, never()).setYield(0);
    }

    @Test void allowlistedOwnedActiveBlockIsKeptAndYieldSuppressed() {
        UUID invasion = UUID.randomUUID(); Entity mob = mock(Entity.class); Block block = mock(Block.class); World world = mock(World.class);
        when(mob.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(block.getType()).thenReturn(Material.DIRT); when(block.getWorld()).thenReturn(world); when(world.getName()).thenReturn("world");
        when(block.getChunk()).thenReturn(mock(org.bukkit.Chunk.class)); when(block.getChunk().getX()).thenReturn(0); when(block.getChunk().getZ()).thenReturn(0);
        PlotService plots = mock(PlotService.class); when(plots.getGuildBlock(0,0,"world")).thenReturn(Optional.of(new GuildBlock(0,0,"world","guild")));
        InvasionRuntime runtime = mock(InvasionRuntime.class); InvasionEngine engine = mock(InvasionEngine.class);
        when(runtime.canDestroy(eq(invasion), eq("guild"))).thenReturn(true);
        when(pdc(mob).get(any(), eq(org.bukkit.persistence.PersistentDataType.STRING))).thenReturn(invasion.toString(), "guild");
        when(engine.recordDestroyedBlock(eq(invasion), anyLong())).thenReturn(InvasionTransition.DAMAGE_RECORDED);
        InvasionListener listener = new InvasionListener(runtime, engine, plots, Set.of(Material.DIRT));
        EntityExplodeEvent event = mock(EntityExplodeEvent.class); when(event.getEntity()).thenReturn(mob); var blocks = new java.util.ArrayList<>(List.of(block)); when(event.blockList()).thenReturn(blocks);
        listener.onExplode(event); verify(event).setYield(0); assertEquals(List.of(block), blocks);
    }

    @Test void protectionBypassAuthorizesOnlyTaggedOwnedActiveBlocks() {
        UUID invasion = UUID.randomUUID();
        Entity mob = mock(Entity.class);
        Block owned = mock(Block.class);
        Block foreign = mock(Block.class);
        World world = mock(World.class);
        org.bukkit.Chunk ownedChunk = mock(org.bukkit.Chunk.class);
        org.bukkit.Chunk foreignChunk = mock(org.bukkit.Chunk.class);
        when(mob.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(pdc(mob).get(any(), eq(org.bukkit.persistence.PersistentDataType.STRING)))
                .thenReturn(invasion.toString(), "guild");
        when(owned.getType()).thenReturn(Material.DIRT);
        when(foreign.getType()).thenReturn(Material.DIRT);
        when(owned.getWorld()).thenReturn(world);
        when(foreign.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(owned.getChunk()).thenReturn(ownedChunk);
        when(foreign.getChunk()).thenReturn(foreignChunk);
        when(ownedChunk.getX()).thenReturn(0);
        when(ownedChunk.getZ()).thenReturn(0);
        when(foreignChunk.getX()).thenReturn(1);
        when(foreignChunk.getZ()).thenReturn(0);
        PlotService plots = mock(PlotService.class);
        when(plots.getGuildBlock(0, 0, "world"))
                .thenReturn(Optional.of(new GuildBlock(0, 0, "world", "guild")));
        when(plots.getGuildBlock(1, 0, "world"))
                .thenReturn(Optional.of(new GuildBlock(1, 0, "world", "other")));
        InvasionRuntime runtime = mock(InvasionRuntime.class);
        when(runtime.canDestroy(invasion, "guild")).thenReturn(true);

        InvasionListener listener = listener(runtime, mock(InvasionEngine.class), plots);

        assertTrue(listener.bypassesProtection(mob, owned));
        assertFalse(listener.bypassesProtection(mob, foreign));
        Entity untagged = mock(Entity.class);
        when(untagged.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        assertFalse(listener.bypassesProtection(untagged, owned));
    }

    @Test void persistenceRollbackPreservesBlockAndCancelsInvasion() {
        UUID invasion = UUID.randomUUID(); Entity mob = mock(Entity.class); Block block = mock(Block.class); World world = mock(World.class);
        when(mob.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(block.getType()).thenReturn(Material.DIRT); when(block.getWorld()).thenReturn(world); when(world.getName()).thenReturn("world");
        when(block.getChunk()).thenReturn(mock(org.bukkit.Chunk.class)); when(block.getChunk().getX()).thenReturn(0); when(block.getChunk().getZ()).thenReturn(0);
        PlotService plots = mock(PlotService.class); when(plots.getGuildBlock(0,0,"world")).thenReturn(Optional.of(new GuildBlock(0,0,"world","guild")));
        InvasionRuntime runtime = mock(InvasionRuntime.class); InvasionEngine engine = mock(InvasionEngine.class);
        when(runtime.canDestroy(eq(invasion), eq("guild"))).thenReturn(true);
        when(pdc(mob).get(any(), eq(org.bukkit.persistence.PersistentDataType.STRING))).thenReturn(invasion.toString(), "guild");
        when(engine.recordDestroyedBlock(eq(invasion), anyLong())).thenReturn(InvasionTransition.NO_CHANGE);
        InvasionListener listener = new InvasionListener(runtime, engine, plots, Set.of(Material.DIRT));
        EntityExplodeEvent event = mock(EntityExplodeEvent.class); when(event.getEntity()).thenReturn(mob); var blocks = new java.util.ArrayList<>(List.of(block)); when(event.blockList()).thenReturn(blocks);
        listener.onExplode(event); verify(runtime).cancel(eq("guild"), anyLong()); assertTrue(blocks.isEmpty());
    }

    @Test void terminalAndWrongClaimArePreserved() {
        UUID invasion = UUID.randomUUID(); Entity mob = mock(Entity.class); Block block = mock(Block.class); World world = mock(World.class);
        when(mob.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class)); when(block.getType()).thenReturn(Material.DIRT); when(block.getWorld()).thenReturn(world); when(world.getName()).thenReturn("world"); when(block.getChunk()).thenReturn(mock(org.bukkit.Chunk.class)); when(block.getChunk().getX()).thenReturn(0); when(block.getChunk().getZ()).thenReturn(0);
        when(pdc(mob).get(any(), eq(org.bukkit.persistence.PersistentDataType.STRING))).thenReturn(invasion.toString(), "guild");
        InvasionRuntime runtime = mock(InvasionRuntime.class); when(runtime.status("guild")).thenReturn(Optional.of(new InvasionState(invasion,"guild","Guild","world",0,64,0,InvasionStatus.DEVASTATED,0,List.of(),new GuildDamage(1,100),0)));
        InvasionListener listener = new InvasionListener(runtime,mock(InvasionEngine.class),mock(PlotService.class),Set.of(Material.DIRT)); EntityExplodeEvent event=mock(EntityExplodeEvent.class); when(event.getEntity()).thenReturn(mob); var blocks=new java.util.ArrayList<>(List.of(block)); when(event.blockList()).thenReturn(blocks); listener.onExplode(event); assertTrue(blocks.isEmpty());
    }

    @Test void untaggedChangeBlockIsPreserved() {
        Entity mob = mock(Entity.class); Block block = mock(Block.class);
        when(mob.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(pdc(mob).get(any(), eq(org.bukkit.persistence.PersistentDataType.STRING))).thenReturn(null);
        EntityChangeBlockEvent event=mock(EntityChangeBlockEvent.class); when(event.getEntity()).thenReturn(mob); when(event.getBlock()).thenReturn(block);
        listener(mock(InvasionRuntime.class),mock(InvasionEngine.class),mock(PlotService.class)).onChange(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    private static org.bukkit.persistence.PersistentDataContainer pdc(Entity entity) { return entity.getPersistentDataContainer(); }
    private static InvasionListener listener(InvasionRuntime runtime, InvasionEngine engine, PlotService plots) { return new InvasionListener(runtime,engine,plots,Set.of(Material.DIRT)); }
}
