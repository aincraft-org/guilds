package org.aincraft.guilds;

import org.aincraft.guilds.models.Alliance;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.AllianceService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.territory.building.FastTravelAccess;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FastTravelSnapshotProviderTest {
    @Test
    void providerRefreshesMembershipCapabilitiesAndAlliancesAfterStateChanges() throws Exception {
        UUID playerId = UUID.randomUUID();
        AtomicBoolean member = new AtomicBoolean(true);
        AtomicBoolean capable = new AtomicBoolean(true);
        AtomicBoolean allied = new AtomicBoolean(true);

        Resident resident = mock(Resident.class);
        when(resident.getUuid()).thenReturn(playerId);
        when(resident.hasGuild()).thenAnswer(ignored -> member.get());
        when(resident.getGuild()).thenReturn("guild-a");
        ResidentService residents = mock(ResidentService.class);
        when(residents.getAllResidents()).thenReturn(List.of(resident));
        when(residents.getResident(playerId)).thenReturn(Optional.of(resident));

        Guild guildA = mock(Guild.class);
        when(guildA.getId()).thenReturn("guild-a");
        Guild guildB = mock(Guild.class);
        when(guildB.getId()).thenReturn("guild-b");
        GuildService guilds = mock(GuildService.class);
        when(guilds.getGuild("guild-a")).thenReturn(Optional.of(guildA));
        when(guilds.getAllGuilds()).thenReturn(List.of(guildA, guildB));

        TechTreeService techTree = mock(TechTreeService.class);
        when(techTree.hasCapability(any(), anyString())).thenAnswer(ignored -> capable.get());

        Alliance alliance = mock(Alliance.class);
        when(alliance.getMemberGuildIds()).thenAnswer(ignored -> allied.get()
                ? Set.of("guild-a", "guild-b") : Set.of("guild-a"));
        AllianceService alliances = mock(AllianceService.class);
        when(alliances.getAllAlliances()).thenReturn(List.of(alliance));

        GuildsServices services = mock(GuildsServices.class);
        when(services.getResidentService()).thenReturn(residents);
        when(services.getGuildService()).thenReturn(guilds);
        when(services.getTechTreeService()).thenReturn(techTree);
        when(services.getAllianceService()).thenReturn(alliances);

        GuildsPlugin plugin = mock(GuildsPlugin.class);
        Field servicesField = GuildsPlugin.class.getDeclaredField("guilds");
        servicesField.setAccessible(true);
        servicesField.set(plugin, services);
        Method providerMethod = GuildsPlugin.class.getDeclaredMethod("preloadFastTravelSnapshots");
        providerMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Function<UUID, FastTravelAccess.FastTravelSnapshot> provider =
                (Function<UUID, FastTravelAccess.FastTravelSnapshot>) providerMethod.invoke(plugin);

        FastTravelAccess.FastTravelSnapshot initial = provider.apply(playerId);
        assertTrue(initial.travelerGuildId().isPresent());
        assertTrue(initial.hasCapability("guild-a", "fast_travel"));
        assertTrue(initial.allied("guild-a", "guild-b"));

        member.set(false);
        capable.set(false);
        allied.set(false);

        FastTravelAccess.FastTravelSnapshot current = provider.apply(playerId);
        assertFalse(current.travelerGuildId().isPresent());
        assertFalse(current.hasCapability("guild-a", "fast_travel"));
        assertFalse(current.allied("guild-a", "guild-b"));
    }
}
