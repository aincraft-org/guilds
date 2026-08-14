package com.azoth.territory.invasion;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvasionMobTagsTest {
    @Test
    void tagsRoundTripAndMalformedValuesAreEmpty() {
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        UUID invasion = UUID.randomUUID();
        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenReturn(invasion.toString(), "guild-7");
        assertEquals(invasion, InvasionMobTags.invasionId(pdc).orElseThrow());
        assertEquals("guild-7", InvasionMobTags.guildId(pdc).orElseThrow());

        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn("bad");
        assertTrue(InvasionMobTags.invasionId(pdc).isEmpty());
        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn(null);
        assertTrue(InvasionMobTags.guildId(pdc).isEmpty());
    }

    @Test
    void guildTagWithoutInvasionTagDoesNotAuthorizeMob() {
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn("guild-7");
        assertFalse(InvasionMobTags.belongsTo(pdc, UUID.randomUUID(), "guild-7"));
    }
}
