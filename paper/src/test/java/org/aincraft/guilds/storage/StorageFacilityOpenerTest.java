package org.aincraft.guilds.storage;

import org.aincraft.guilds.storage.gui.GuildStorageGUI;
import org.aincraft.guilds.territory.building.FacilityAnchorValidator;
import org.aincraft.guilds.territory.building.StorageFacilityInteractEvent;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.model.ZoneType;
import org.aincraft.guilds.territory.permission.GovernanceRegistry;
import org.aincraft.guilds.territory.permission.GovernanceSource;
import org.aincraft.guilds.territory.permission.GuildBody;
import org.aincraft.guilds.territory.permission.GuildToggles;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.aincraft.guilds.territory.building.BuildingConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorageFacilityOpenerTest {
    @Mock
    private Server server;
    @Mock
    private World world;
    @Mock
    private Block block;
    @Mock
    private Player player;
    @Mock
    private GuildStorageGUI storageGui;
    @Mock
    private PluginManager pluginManager;

    private UUID memberId;
    private UUID outsiderId;
    private UUID allianceMemberId;
    private String guildId;
    private String allianceGuildId;
    private SettlementFacility facility;
    private TerritoryRegistry territories;
    private FacilityRegistry facilities;
    private FacilityAnchorValidator anchors;
    private GovernanceRegistry governance;
    private StorageFacilityOpener opener;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        outsiderId = UUID.randomUUID();
        allianceMemberId = UUID.randomUUID();
        guildId = "guild-home";
        allianceGuildId = "guild-ally";

        territories = new TerritoryRegistry();
        territories.register(new Territory(
                "territory-1",
                "Territory",
                "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0),
                        new BlockPos(10, 0),
                        new BlockPos(10, 10),
                        new BlockPos(0, 10))),
                List.of(),
                ZoneType.WILDERNESS,
                Government.anarchy(),
                List.of(),
                guildId));
        facilities = new FacilityRegistry(territories);
        facility = new SettlementFacility(
                "storage-1", "Vault", "territory-1", FacilityType.STORAGE, "world", 5, 64, 5);
        facilities.register(facility);

        BuildingConfig config = new BuildingConfig(
                30_000L, Map.of(FacilityType.STORAGE, Set.of(Material.BARREL)), 0L, 0L);
        anchors = new FacilityAnchorValidator(server, territories, facilities, config);
        governance = new GovernanceRegistry(
                territories,
                new MultiGuildSource(guildId, memberId, allianceGuildId, allianceMemberId));

        when(server.getWorld("world")).thenReturn(world);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getBlockAt(5, 64, 5)).thenReturn(block);
        when(block.getType()).thenReturn(Material.BARREL);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 5.5, 64, 5.5));

        opener = new StorageFacilityOpener(
                facilities, territories, anchors, governance, storageGui, pluginManager);
    }

    @Test
    void opensGuiForMemberAtActiveAnchor() {
        when(player.getUniqueId()).thenReturn(memberId);

        StorageFacilityOpener.Result result = opener.tryOpen(player, facility);

        assertEquals(StorageFacilityOpener.Outcome.OPENED, result.outcome());
        verify(storageGui).open(player, facility, guildId);
        verify(pluginManager).callEvent(any(StorageFacilityInteractEvent.class));
    }

    @Test
    void deniesInactiveAnchorMaterial() {
        when(player.getUniqueId()).thenReturn(memberId);
        when(block.getType()).thenReturn(Material.STONE);

        StorageFacilityOpener.Result result = opener.tryOpen(player, facility);

        assertEquals(StorageFacilityOpener.Outcome.DENIED, result.outcome());
        assertTrue(result.message().contains("inactive"));
        verify(storageGui, never()).open(any(), any(), any());
    }

    @Test
    void deniesOutsider() {
        when(player.getUniqueId()).thenReturn(outsiderId);

        StorageFacilityOpener.Result result = opener.tryOpen(player, facility);

        assertEquals(StorageFacilityOpener.Outcome.DENIED, result.outcome());
        verify(storageGui, never()).open(any(), any(), any());
    }

    @Test
    void deniesAllianceMemberNotInGoverningGuild() {
        when(player.getUniqueId()).thenReturn(allianceMemberId);

        StorageFacilityOpener.Result result = opener.tryOpen(player, facility);

        assertEquals(StorageFacilityOpener.Outcome.DENIED, result.outcome());
        verify(storageGui, never()).open(any(), any(), any());
    }

    @Test
    void deniesRemoteCommandWhenNotStandingOnStorageAnchor() {
        when(player.getUniqueId()).thenReturn(memberId);
        when(player.getLocation()).thenReturn(new Location(world, 50.5, 64, 50.5));

        StorageFacilityOpener.Result result = opener.tryOpenAtLocation(player);

        assertEquals(StorageFacilityOpener.Outcome.DENIED, result.outcome());
        verify(storageGui, never()).open(any(), any(), any());
    }

    @Test
    void deniesWildernessWithoutGoverningGuild() {
        territories.register(new Territory(
                "wild-1",
                "Wild",
                "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(20, 0),
                        new BlockPos(30, 0),
                        new BlockPos(30, 10),
                        new BlockPos(20, 10))),
                List.of(),
                ZoneType.WILDERNESS,
                Government.anarchy(),
                List.of(),
                null));
        SettlementFacility wildStorage = new SettlementFacility(
                "wild-storage", "Wild Vault", "wild-1", FacilityType.STORAGE, "world", 25, 64, 5);
        facilities.register(wildStorage);
        when(player.getUniqueId()).thenReturn(memberId);
        when(world.getBlockAt(25, 64, 5)).thenReturn(block);
        when(block.getType()).thenReturn(Material.BARREL);

        StorageFacilityOpener.Result result = opener.tryOpen(player, wildStorage);

        assertEquals(StorageFacilityOpener.Outcome.DENIED, result.outcome());
        verify(storageGui, never()).open(any(), any(), any());
    }

    @Test
    void honoursCancelledInteractionEvent() {
        when(player.getUniqueId()).thenReturn(memberId);
        doAnswer(invocation -> {
            StorageFacilityInteractEvent event = invocation.getArgument(0);
            event.setCancelled(true);
            return null;
        }).when(pluginManager).callEvent(any());

        StorageFacilityOpener.Result result = opener.tryOpen(player, facility);

        assertEquals(StorageFacilityOpener.Outcome.DENIED, result.outcome());
        verify(storageGui, never()).open(any(), any(), any());
    }

    @Test
    void emitsInteractionEventWithGuildId() {
        when(player.getUniqueId()).thenReturn(memberId);
        ArgumentCaptor<StorageFacilityInteractEvent> captor = ArgumentCaptor.forClass(StorageFacilityInteractEvent.class);

        opener.tryOpen(player, facility);

        verify(pluginManager).callEvent(captor.capture());
        assertEquals(guildId, captor.getValue().guildId());
        assertEquals(facility, captor.getValue().facility());
    }

    private static final class MultiGuildSource implements GovernanceSource {
        private final String homeGuildId;
        private final UUID homeMember;
        private final String allyGuildId;
        private final UUID allyMember;

        private MultiGuildSource(String homeGuildId, UUID homeMember, String allyGuildId, UUID allyMember) {
            this.homeGuildId = homeGuildId;
            this.homeMember = homeMember;
            this.allyGuildId = allyGuildId;
            this.allyMember = allyMember;
        }

        @Override
        public Optional<GuildBody> guild(String id) {
            if (homeGuildId.equals(id)) {
                return Optional.of(body(homeGuildId, homeMember));
            }
            if (allyGuildId.equals(id)) {
                return Optional.of(body(allyGuildId, allyMember));
            }
            return Optional.empty();
        }

        private static GuildBody body(String id, UUID member) {
            return new GuildBody(
                    id,
                    id,
                    Government.anarchy(),
                    List.of(member.toString()),
                    GuildToggles.defaults(),
                    Map.of());
        }

        @Override
        public List<GuildBody> guildsForMember(String holderId) {
            return allGuilds().stream().filter(body -> body.containsMember(holderId)).toList();
        }

        @Override
        public Optional<org.aincraft.guilds.territory.permission.AllianceBody> allianceContainingGuild(String guildId) {
            return Optional.empty();
        }

        @Override
        public List<GuildBody> allGuilds() {
            return List.of(body(homeGuildId, homeMember), body(allyGuildId, allyMember));
        }

        @Override
        public List<org.aincraft.guilds.territory.permission.AllianceBody> allAlliances() {
            return List.of();
        }
    }
}
