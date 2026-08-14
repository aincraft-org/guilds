package com.azoth.territory.invasion;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvasionBossBarsTest {
    @Test
    void formatsTitleProgressAndColor() {
        var bars = new InvasionBossBars(96);
        var record = new InvasionRecord(UUID.randomUUID(), "guild-7", "Guild A", "world", 0, 64, 0,
                InvasionStatus.ACTIVE, 1, List.of(UUID.randomUUID(), UUID.randomUUID()), new GuildDamage(42, 42), 0);
        BossBar bar = bars.bar(record, 3);
        assertEquals(Component.text("Guild A Invasion — Wave 2/3 — Damage 42%"), bar.name());
        assertEquals(1f, bar.progress());
        assertEquals(BossBar.Color.RED, bar.color());
        var purple = bars.bar(new InvasionRecord(record.invasionId(), record.guildId(), record.guildName(), record.worldId(),
                0, 64, 0, InvasionStatus.ACTIVE, 1, List.of(), new GuildDamage(75, 75), 0), 3);
        assertEquals(BossBar.Color.PURPLE, purple.color());
        assertEquals(1f, purple.progress());
    }

    @Test
    void reconcilesResidentRegardlessOfLocationAndNearbyOnlyInWorld() {
        var bars = new InvasionBossBars(96);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Player resident = mock(Player.class), nearby = mock(Player.class), far = mock(Player.class), other = mock(Player.class);
        UUID residentId = UUID.randomUUID();
        when(resident.getUniqueId()).thenReturn(residentId);
        when(nearby.getUniqueId()).thenReturn(UUID.randomUUID());
        when(far.getUniqueId()).thenReturn(UUID.randomUUID());
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(resident.getWorld()).thenReturn(mock(World.class));
        when(nearby.getWorld()).thenReturn(world);
        when(far.getWorld()).thenReturn(world);
        when(other.getWorld()).thenReturn(mock(World.class));
        when(nearby.getLocation()).thenReturn(new org.bukkit.Location(world, 10, 64, 0));
        when(far.getLocation()).thenReturn(new org.bukkit.Location(world, 200, 64, 0));
        var record = new InvasionRecord(UUID.randomUUID(), "guild-7", "Guild A", "world", 0, 64, 0,
                InvasionStatus.ACTIVE, 0, List.of(UUID.randomUUID()), new GuildDamage(0, 0), 0);
        assertTrue(bars.shouldShow(resident, record, Set.of(residentId)));
        assertTrue(bars.shouldShow(nearby, record, Set.of(residentId)));
        assertFalse(bars.shouldShow(far, record, Set.of(residentId)));
        assertFalse(bars.shouldShow(other, record, Set.of(residentId)));
    }
}
