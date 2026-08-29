package org.aincraft.guilds.territory.building;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Location;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.FastTravelMode;
import org.aincraft.guilds.territory.model.FastTravelPolicy;
import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FastTravelFacilityValidatorTest {
    private Server server;
    private World world;
    private GuildService guilds;
    private TechTreeService tech;
    private TerritoryRegistry territories;
    private FacilityRegistry facilities;
    private Guild guild;
    private Map<String, Material> blocks;
    private FastTravelFacilityValidator validator;

    @BeforeEach
    void setUp() {
        server = mock(Server.class);
        world = mock(World.class);
        guilds = mock(GuildService.class);
        tech = mock(TechTreeService.class);
        guild = mock(Guild.class);
        blocks = new HashMap<>();
        when(server.getWorld("world")).thenReturn(world);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int y = invocation.getArgument(1);
            int z = invocation.getArgument(2);
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(blocks.getOrDefault(key(x, y, z), Material.AIR));
            return block;
        });
        when(guild.getId()).thenReturn("g1");
        when(guild.getName()).thenReturn("Guild One");
        when(guilds.getGuildById("g1")).thenReturn(Optional.of(guild));
        when(guilds.getGuildSpawn("Guild One"))
                .thenReturn(Optional.of(new Location(5, 64, 5, "world")));
        when(tech.hasCapability(eq(guild), any(String.class))).thenReturn(true);
        territories = new TerritoryRegistry(List.of(territory(FastTravelPolicy.defaults())));
        facilities = new FacilityRegistry(territories);
        BuildingConfig config = new BuildingConfig(60_000L, Map.of(
                FacilityType.GUILD_CRYSTAL, Set.of(Material.AMETHYST_BLOCK),
                FacilityType.TELEPORT_TERMINAL, Set.of(Material.LODESTONE),
                FacilityType.BOAT, Set.of(Material.OAK_PLANKS),
                FacilityType.AIRSHIP, Set.of(Material.IRON_BLOCK)),
                100L, 60_000L);
        validator = new FastTravelFacilityValidator(server, territories, facilities, guilds, tech, config,
                apiOnlyBlockStateSemantics());
    }

    @Test
    void crystalRequiresExactCurrentSpawnWorldAndBlock() {
        SettlementFacility crystal = facility("crystal", FacilityType.GUILD_CRYSTAL, 5, 64, 5);
        blocks.put(key(5, 64, 5), Material.AMETHYST_BLOCK);
        FacilityRegistry candidate = registryWith(crystal);

        assertTrue(validator.validateCandidate(crystal, candidate).valid());

        when(guilds.getGuildSpawn("Guild One"))
                .thenReturn(Optional.of(new Location(6, 64, 5, "world")));
        assertFalse(validator.validateCandidate(crystal, candidate).valid());
        assertTrue(validator.validateCrystalSpawn(crystal).status() == AnchorStatus.SPAWN_MISMATCH);

        when(guilds.getGuildSpawn("Guild One"))
                .thenReturn(Optional.of(new Location(5, 64, 5, "other-world")));
        assertFalse(validator.validateCrystalSpawn(crystal).valid());
    }

    @Test
    void governanceLossImmediatelyInactivatesAndRebindRequiresRevalidation() {
        SettlementFacility crystal = facility("crystal", FacilityType.GUILD_CRYSTAL, 5, 64, 5);
        blocks.put(key(5, 64, 5), Material.AMETHYST_BLOCK);
        facilities.register(crystal);
        assertTrue(validator.isActive(crystal));

        territories.register(territory(FastTravelPolicy.defaults()).withoutGoverningGuild());
        assertFalse(validator.isActive(crystal));
        territories.register(territory(FastTravelPolicy.defaults()).withGoverningGuild("g1"));
        assertTrue(validator.isActive(crystal));
    }

    @Test
    void inactiveCrystalStillReservesOnePerGuildSlot() {
        SettlementFacility first = facility("crystal-one", FacilityType.GUILD_CRYSTAL, 5, 64, 5);
        SettlementFacility second = facility("crystal-two", FacilityType.GUILD_CRYSTAL, 8, 64, 8);
        blocks.put(key(5, 64, 5), Material.STONE);
        blocks.put(key(8, 64, 8), Material.AMETHYST_BLOCK);
        FacilityRegistry candidate = registryWith(first, second);

        assertFalse(validator.validateCandidate(second, candidate).valid());
        assertTrue(validator.validateCandidate(second, candidate).status() == AnchorStatus.CARDINALITY_EXCEEDED);
    }

    @Test
    void quotaIsIndependentPerTransportTypeAndLoweringDoesNotDisableExistingFacility() {
        Map<FacilityType, Integer> quotas = new java.util.EnumMap<>(FacilityType.class);
        quotas.put(FacilityType.BOAT, 1);
        quotas.put(FacilityType.AIRSHIP, 1);
        FastTravelPolicy policy = new FastTravelPolicy(quotas, Set.of(
                FastTravelMode.BOAT, FastTravelMode.AIRSHIP));
        territories.register(territory(policy));
        blocks.put(key(19, 64, 20), Material.WATER);
        blocks.put(key(20, 64, 19), Material.WATER);
        blocks.put(key(21, 64, 20), Material.WATER);
        SettlementFacility boat = facility("boat", FacilityType.BOAT, 20, 64, 20);
        SettlementFacility airship = facility("airship", FacilityType.AIRSHIP, 40, 64, 40);
        blocks.put(key(20, 64, 20), Material.OAK_PLANKS);
        blocks.put(key(40, 64, 40), Material.IRON_BLOCK);
        blocks.put(key(40, 63, 40), Material.STONE);
        for (int y = 65; y <= 80; y++) {
            blocks.put(key(40, y, 40), Material.AIR);
        }
        FacilityRegistry candidate = registryWith(boat, airship);
        assertTrue(validator.validateCandidate(boat, candidate).valid());
        assertTrue(validator.validateCandidate(airship, candidate).valid());
        facilities.register(boat);
        assertTrue(validator.isActive(boat));

        Map<FacilityType, Integer> lowered = new java.util.EnumMap<>(FacilityType.class);
        lowered.put(FacilityType.BOAT, 0);
        lowered.put(FacilityType.AIRSHIP, 1);
        territories.register(territory(new FastTravelPolicy(lowered, Set.of(
                FastTravelMode.BOAT, FastTravelMode.AIRSHIP))));
        assertFalse(validator.validateCandidate(boat, registryWith(boat)).valid());
        assertTrue(validator.isActive(boat));
    }

    @Test
    void boatOnlyAcceptsBoundedAdjacentWaterWindowWithClearSpace() {
        SettlementFacility boat = facility("boat", FacilityType.BOAT, 20, 64, 20);
        blocks.put(key(20, 64, 20), Material.OAK_PLANKS);
        blocks.put(key(19, 64, 20), Material.WATER);
        blocks.put(key(20, 64, 19), Material.WATER);
        blocks.put(key(21, 64, 20), Material.WATER);
        blocks.put(key(19, 65, 20), Material.AIR);
        blocks.put(key(19, 66, 20), Material.AIR);
        blocks.put(key(20, 65, 19), Material.AIR);
        blocks.put(key(20, 66, 19), Material.AIR);
        blocks.put(key(21, 65, 20), Material.AIR);
        blocks.put(key(21, 66, 20), Material.AIR);

        assertTrue(validator.validateBoatAnchor(new org.bukkit.Location(world, 20, 64, 20)).valid());
        blocks.put(key(19, 64, 20), Material.STONE);
        blocks.put(key(20, 64, 19), Material.STONE);
        blocks.put(key(21, 64, 20), Material.STONE);
        assertFalse(validator.validateBoatAnchor(new org.bukkit.Location(world, 20, 64, 20)).valid());
    }

    @Test
    void airshipRequiresPlatformAndClearVerticalSpace() {
        blocks.put(key(40, 64, 40), Material.IRON_BLOCK);
        blocks.put(key(40, 63, 40), Material.STONE);
        for (int y = 65; y <= 80; y++) {
            blocks.put(key(40, y, 40), Material.AIR);
        }
        org.bukkit.Location anchor = new org.bukkit.Location(world, 40, 64, 40);
        assertTrue(validator.validateAirshipAnchor(anchor).valid());
        blocks.put(key(40, 70, 40), Material.STONE);
        assertFalse(validator.validateAirshipAnchor(anchor).valid());
        assertTrue(validator.validateAirshipAnchor(anchor).status() == AnchorStatus.AIRSHIP_CLEARANCE_BLOCKED);
    }

    @Test
    void boatValidationIsBoundedToConfiguredLocalWindow() {
        blocks.put(key(20, 64, 20), Material.OAK_PLANKS);

        assertFalse(validator.validateBoatAnchor(new org.bukkit.Location(world, 20, 64, 20)).valid());
        org.mockito.Mockito.verify(world, org.mockito.Mockito.atMost(25))
                .getBlockAt(anyInt(), anyInt(), anyInt());
    }

    private static FastTravelFacilityValidator.BlockStateSemantics apiOnlyBlockStateSemantics() {
        return new FastTravelFacilityValidator.BlockStateSemantics() {
            @Override
            public boolean isAir(Block block) {
                Material type = block.getType();
                return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
            }

            @Override
            public boolean isSolid(Block block) {
                Material type = block.getType();
                return type == Material.STONE || type == Material.IRON_BLOCK;
            }
        };
    }

    private FacilityRegistry registryWith(SettlementFacility... entries) {
        FacilityRegistry registry = new FacilityRegistry(territories);

        for (SettlementFacility entry : entries) {
            registry.register(entry);
        }
        return registry;
    }

    private SettlementFacility facility(String id, FacilityType type, int x, int y, int z) {
        return new SettlementFacility(id, id, "t1", type, "world", x, y, z);
    }

    private static Territory territory(FastTravelPolicy policy) {
        return new Territory("t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))),
                List.of(), org.aincraft.guilds.territory.model.ZoneType.WILDERNESS,
                Government.anarchy(), List.of(), "g1", policy);
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
