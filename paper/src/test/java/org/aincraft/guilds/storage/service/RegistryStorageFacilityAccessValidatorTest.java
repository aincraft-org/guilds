package org.aincraft.guilds.storage.service;

import org.aincraft.guilds.territory.building.BuildingConfig;
import org.aincraft.guilds.territory.building.FacilityAnchorValidator;
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
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistryStorageFacilityAccessValidatorTest {
    @Mock
    private Server server;
    @Mock
    private World world;
    @Mock
    private Block block;

    private UUID actor;
    private String guildId;
    private SettlementFacility facility;
    private TerritoryRegistry territories;
    private FacilityRegistry facilities;
    private FacilityAnchorValidator anchors;
    private GovernanceRegistry governance;

    @BeforeEach
    void setUp() {
        actor = UUID.randomUUID();
        guildId = "guild-1";
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
        facility = new SettlementFacility("storage-1", "Storage", "territory-1", FacilityType.STORAGE, "world", 5, 64, 5);
        facilities.register(facility);
        BuildingConfig config = new BuildingConfig(30_000L, Map.of(FacilityType.STORAGE, Set.of(Material.BARREL)), 0L, 0L);
        anchors = new FacilityAnchorValidator(server, territories, facilities, config);
        governance = new GovernanceRegistry(territories, new FixedGovernanceSource(guildId, actor));
        when(server.getWorld("world")).thenReturn(world);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getBlockAt(5, 64, 5)).thenReturn(block);
        when(block.getType()).thenReturn(Material.BARREL);
    }

    @Test
    void allowsActiveStorageFacilityAtActorLocation() {
        RegistryStorageFacilityAccessValidator validator = validatorAt(5, 64, 5);
        StorageResult<Void> result = validator.validateMutationAccess(actor, guildId, facility.id());
        assertEquals(StorageResult.Status.SUCCESS, result.status());
    }

    @Test
    void rejectsForeignFacilityId() {
        RegistryStorageFacilityAccessValidator validator = validatorAt(5, 64, 5);
        StorageResult<Void> result = validator.validateMutationAccess(actor, guildId, "missing");
        assertEquals(StorageResult.Status.PERMISSION_DENIED, result.status());
    }

    @Test
    void rejectsActorAtDifferentLocation() {
        RegistryStorageFacilityAccessValidator validator = validatorAt(99, 64, 99);
        StorageResult<Void> result = validator.validateMutationAccess(actor, guildId, facility.id());
        assertEquals(StorageResult.Status.PERMISSION_DENIED, result.status());
    }

    @Test
    void rejectsInactiveAnchorMaterial() {
        when(block.getType()).thenReturn(Material.STONE);
        RegistryStorageFacilityAccessValidator validator = validatorAt(5, 64, 5);
        StorageResult<Void> result = validator.validateMutationAccess(actor, guildId, facility.id());
        assertEquals(StorageResult.Status.PERMISSION_DENIED, result.status());
    }

    @Test
    void rejectsFacilityGovernedByDifferentGuild() {
        RegistryStorageFacilityAccessValidator validator = validatorAt(5, 64, 5);
        StorageResult<Void> result = validator.validateMutationAccess(actor, "other-guild", facility.id());
        assertEquals(StorageResult.Status.PERMISSION_DENIED, result.status());
    }

    private RegistryStorageFacilityAccessValidator validatorAt(int x, int y, int z) {
        return new RegistryStorageFacilityAccessValidator(
                id -> actor.equals(id) ? Optional.of(new PlayerLocationSource.BlockLocation("world", x, y, z)) : Optional.empty(),
                facilities,
                anchors,
                governance);
    }

    private static final class FixedGovernanceSource implements GovernanceSource {
        private final String guildId;
        private final UUID member;

        private FixedGovernanceSource(String guildId, UUID member) {
            this.guildId = guildId;
            this.member = member;
        }

        @Override
        public Optional<GuildBody> guild(String id) {
            if (!guildId.equals(id)) {
                return Optional.empty();
            }
            return Optional.of(new GuildBody(
                    guildId,
                    "Guild",
                    Government.anarchy(),
                    List.of(member.toString()),
                    GuildToggles.defaults(),
                    Map.of()));
        }

        @Override
        public List<GuildBody> guildsForMember(String holderId) {
            return guild(guildId).stream().filter(body -> body.containsMember(holderId)).toList();
        }

        @Override
        public Optional<org.aincraft.guilds.territory.permission.AllianceBody> allianceContainingGuild(String guildId) {
            return Optional.empty();
        }

        @Override
        public List<GuildBody> allGuilds() {
            return guild(guildId).stream().toList();
        }

        @Override
        public List<org.aincraft.guilds.territory.permission.AllianceBody> allAlliances() {
            return List.of();
        }
    }
}
