package com.azoth.territory.invasion;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvasionMobSpawnerTest {
    private static final UUID INVASION_ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();

    @Test
    void spawnsConfiguredCountOnSolidFloorWithTwoPassableBlocksAndTags() {
        Plugin plugin = mockPlugin();
        World world = readyWorld(plugin);
        Entity entity = mock(Entity.class);
        when(entity.getUniqueId()).thenReturn(ENTITY_ID);
        when(world.spawnEntity(any(Location.class), eq(EntityType.ZOMBIE))).thenReturn(entity);
        org.bukkit.persistence.PersistentDataContainer pdc =
                mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        InvasionRecord record = record();

        List<UUID> spawned = new InvasionMobSpawner(plugin, fixedRandom()).spawn(
                record, new Wave(List.of(new MobEntry("ZOMBIE", 2))), 2, 1, location -> true);

        assertEquals(List.of(ENTITY_ID, ENTITY_ID), spawned);
        verify(world, times(2)).spawnEntity(any(Location.class), eq(EntityType.ZOMBIE));
        verify(entity, times(2)).getUniqueId();
        verify(entity, times(2)).getPersistentDataContainer();
    }

    @Test
    void rejectsWildernessAndOtherGuildClaimsWithoutSpawning() {
        Plugin plugin = mockPlugin();
        readyWorld(plugin);
        InvasionMobSpawner spawner = new InvasionMobSpawner(plugin, fixedRandom());

        assertTrue(spawner.spawn(record(), new Wave(List.of(new MobEntry("ZOMBIE", 1))), 1, 3,
                location -> false).isEmpty());
        assertTrue(spawner.spawn(record(), new Wave(List.of(new MobEntry("ZOMBIE", 1))), 1, 3,
                location -> false).isEmpty());
    }

    @Test
    void rejectsUnloadedChunksAndBlockedHeadroom() {
        Plugin plugin = mockPlugin();
        World world = readyWorld(plugin);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        InvasionMobSpawner spawner = new InvasionMobSpawner(plugin, fixedRandom());
        assertTrue(spawner.spawn(record(), wave(), 1, 2, location -> true).isEmpty());
        verify(world, never()).spawnEntity(any(Location.class), any(EntityType.class));

        org.bukkit.block.Block floor = mock(org.bukkit.block.Block.class);
        org.bukkit.block.Block head = mock(org.bukkit.block.Block.class);
        org.bukkit.block.Block upper = mock(org.bukkit.block.Block.class);
        doReturn(floor).when(world).getBlockAt(anyInt(), eq(64), anyInt());
        doReturn(head).when(world).getBlockAt(anyInt(), eq(65), anyInt());
        doReturn(upper).when(world).getBlockAt(anyInt(), eq(66), anyInt());
        when(floor.isPassable()).thenReturn(false);
        when(head.isPassable()).thenReturn(false);
        when(upper.isPassable()).thenReturn(true);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        assertTrue(spawner.spawn(record(), wave(), 1, 2, location -> true).isEmpty());
        verify(world, never()).spawnEntity(any(Location.class), any(EntityType.class));
    }

    @Test
    void exhaustsConfiguredAttemptsPerMobAndDoesNotOverSpawn() {
        Plugin plugin = mockPlugin();
        World world = readyWorld(plugin);
        InvasionMobSpawner spawner = new InvasionMobSpawner(plugin, fixedRandom());
        List<UUID> spawned = spawner.spawn(record(), new Wave(List.of(new MobEntry("ZOMBIE", 3))), 1, 4,
                location -> false);
        assertTrue(spawned.isEmpty());
        verify(world, times(12)).getHighestBlockYAt(anyInt(), anyInt());
        verify(world, never()).spawnEntity(any(Location.class), any(EntityType.class));
    }

    @Test
    void rejectsInvalidAndOverflowRadiusBeforeSampling() {
        Plugin plugin = mockPlugin();
        World world = readyWorld(plugin);
        InvasionMobSpawner spawner = new InvasionMobSpawner(plugin, fixedRandom());
        Wave wave = wave();

        assertTrue(spawner.spawn(record(), wave, -1, 1, location -> true).isEmpty());
        assertTrue(spawner.spawn(record(), wave, Integer.MAX_VALUE, 1, location -> true).isEmpty());
        assertTrue(spawner.spawn(record(), wave, (Integer.MAX_VALUE - 1) / 2 + 1, 1, location -> true).isEmpty());
        verify(world, never()).getHighestBlockYAt(anyInt(), anyInt());
        verify(world, never()).spawnEntity(any(Location.class), any(EntityType.class));
    }

    @Test
    void rejectsUnloadedCandidateBeforeQueryingHeight() {
        Plugin plugin = mockPlugin();
        World world = readyWorld(plugin);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        InvasionMobSpawner spawner = new InvasionMobSpawner(plugin, fixedRandom());
        assertTrue(spawner.spawn(record(), wave(), 1, 1, location -> true).isEmpty());
        verify(world, never()).getHighestBlockYAt(anyInt(), anyInt());
    }

    @Test
    void rejectsNonSolidFloorEvenWhenNotPassable() {
        Plugin plugin = mockPlugin();
        World world = readyWorld(plugin);
        org.bukkit.block.Block floor = mock(org.bukkit.block.Block.class);
        when(floor.isPassable()).thenReturn(false);
        doReturn(floor).when(world).getBlockAt(anyInt(), eq(64), anyInt());
        InvasionMobSpawner spawner = new InvasionMobSpawner(plugin, fixedRandom(), block -> false);
        assertTrue(spawner.spawn(record(), wave(), 1, 1, location -> true).isEmpty());
        verify(world, never()).spawnEntity(any(Location.class), any(EntityType.class));
    }

    @Test
    void rollsBackAlreadySpawnedEntitiesWhenLaterSpawnFails() {
        Plugin plugin = mockPlugin();
        World world = readyWorld(plugin);
        Entity first = mock(Entity.class);
        UUID firstId = UUID.randomUUID();
        when(first.getUniqueId()).thenReturn(firstId);
        when(first.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(world.spawnEntity(any(Location.class), eq(EntityType.ZOMBIE)))
                .thenReturn(first)
                .thenThrow(new IllegalStateException("spawn failed"));

        assertThrows(IllegalStateException.class, () -> new InvasionMobSpawner(plugin, fixedRandom()).spawn(
                record(), new Wave(List.of(new MobEntry("ZOMBIE", 2))), 1, 1, location -> true));

        verify(first).remove();
    }

    private static InvasionRecord record() {
        return new InvasionRecord(INVASION_ID, "guild-7", "Guild Seven", "world", 10, 64, 20,
                InvasionStatus.ACTIVE, 0, List.of(), new GuildDamage(0, 0), 0);
    }

    private static Wave wave() {
        return new Wave(List.of(new MobEntry("ZOMBIE", 1)));
    }

    private static Random fixedRandom() {
        return new Random(0) {
            @Override public int nextInt(int bound) { return 0; }
        };
    }

    private static Plugin mockPlugin() {
        Plugin plugin = mock(Plugin.class);
        doReturn("AzothTerritory").when(plugin).getName();
        return plugin;
    }

    private static World readyWorld(Plugin plugin) {
        var server = mock(org.bukkit.Server.class);
        World world = mock(World.class);
        doReturn(server).when(plugin).getServer();
        doReturn(world).when(server).getWorld("world");
        doReturn(true).when(world).isChunkLoaded(anyInt(), anyInt());
        doReturn(64).when(world).getHighestBlockYAt(anyInt(), anyInt());
        org.bukkit.block.Block floor = mock(org.bukkit.block.Block.class);
        org.bukkit.block.Block head = mock(org.bukkit.block.Block.class);
        org.bukkit.block.Block upper = mock(org.bukkit.block.Block.class);
        doReturn(false).when(floor).isPassable();
        doReturn(floor).when(world).getBlockAt(anyInt(), eq(64), anyInt());
        doReturn(head).when(world).getBlockAt(anyInt(), eq(65), anyInt());
        doReturn(upper).when(world).getBlockAt(anyInt(), eq(66), anyInt());
        doReturn(Material.STONE).when(floor).getType();
        doReturn(true).when(head).isPassable();
        doReturn(true).when(upper).isPassable();
        return world;
    }
}
