package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.bukkit.entity.Villager;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuildBankerNpcTest {
    @Test
    void configureTagsPersistentWanderingBanker() {
        Villager villager = mock(Villager.class);
        Set<String> tags = new HashSet<>();
        when(villager.getScoreboardTags()).thenReturn(tags);
        when(villager.addScoreboardTag(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            tags.add(invocation.getArgument(0));
            return true;
        });
        SettlementFacility facility = new SettlementFacility(
                "vault-1", "Minty", "t1", FacilityType.BANK, "world", 5, 64, 5);

        GuildBankerNpc.configure(villager, facility);

        assertTrue(tags.contains(GuildBankerNpc.TAG));
        assertTrue(tags.contains(GuildBankerNpc.FACILITY_TAG_PREFIX + "vault-1"));
        verify(villager).setPersistent(true);
        verify(villager).setRemoveWhenFarAway(false);
        verify(villager).setAI(true);
        assertEquals(Set.of(GuildBankerNpc.TAG, GuildBankerNpc.FACILITY_TAG_PREFIX + "vault-1"), tags);
    }
}
