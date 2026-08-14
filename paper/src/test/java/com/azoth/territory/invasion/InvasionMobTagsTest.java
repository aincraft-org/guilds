package com.azoth.territory.invasion;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class InvasionMobTagsTest {
    @Test
    void tagsRoundTripAndMalformedValuesAreEmpty() {
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        UUID invasion = UUID.randomUUID();
        NamespacedKey invasionKey = new NamespacedKey("azothterritory", "invasion_id");
        NamespacedKey guildKey = new NamespacedKey("azothterritory", "invasion_guild_id");
        when(pdc.get(eq(invasionKey), eq(PersistentDataType.STRING))).thenReturn(invasion.toString());
        when(pdc.get(eq(guildKey), eq(PersistentDataType.STRING))).thenReturn("guild-7");
        assertEquals(invasion, InvasionMobTags.invasionId(pdc).orElseThrow());
        assertEquals("guild-7", InvasionMobTags.guildId(pdc).orElseThrow());

        when(pdc.get(eq(invasionKey), eq(PersistentDataType.STRING))).thenReturn("bad");
        assertTrue(InvasionMobTags.invasionId(pdc).isEmpty());
        when(pdc.get(eq(guildKey), eq(PersistentDataType.STRING))).thenReturn(null);
        assertTrue(InvasionMobTags.guildId(pdc).isEmpty());
    }

    @Test
    void writesOnlyCanonicalInvasionAndGuildPdcEntries() {
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        UUID invasion = UUID.randomUUID();
        new InvasionMobTags(mock(org.bukkit.plugin.Plugin.class)).tag(pdc, invasion, "guild-7");
        verify(pdc).set(new NamespacedKey("azothterritory", "invasion_id"), PersistentDataType.STRING, invasion.toString());
        verify(pdc).set(new NamespacedKey("azothterritory", "invasion_guild_id"), PersistentDataType.STRING, "guild-7");
        verifyNoMoreInteractions(pdc);
    }

    @Test
    void ignoresDecoyNamespaceAndRejectsNonCanonicalUuid() {
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        NamespacedKey canonical = new NamespacedKey("azothterritory", "invasion_id");
        NamespacedKey decoy = new NamespacedKey("other", "invasion_id");
        when(pdc.get(eq(canonical), eq(PersistentDataType.STRING))).thenReturn("550e8400-e29b-41d4-a716-446655440000");
        when(pdc.get(eq(decoy), eq(PersistentDataType.STRING))).thenReturn("550E8400-E29B-41D4-A716-446655440000");
        assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), InvasionMobTags.invasionId(pdc).orElseThrow());
        when(pdc.get(eq(canonical), eq(PersistentDataType.STRING))).thenReturn("550E8400-E29B-41D4-A716-446655440000");
        assertTrue(InvasionMobTags.invasionId(pdc).isEmpty());
    }

    @Test
    void guildTagWithoutInvasionTagDoesNotAuthorizeMob() {
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        NamespacedKey guildKey = new NamespacedKey("azothterritory", "invasion_guild_id");
        when(pdc.get(eq(guildKey), eq(PersistentDataType.STRING))).thenReturn("guild-7");
        assertFalse(InvasionMobTags.belongsTo(pdc, UUID.randomUUID(), "guild-7"));
    }
}
