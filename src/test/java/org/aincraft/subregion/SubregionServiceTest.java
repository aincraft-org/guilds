package org.aincraft.subregion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.service.TerritoryService;
import org.aincraft.service.PermissionService;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for SubregionService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubregionService")
class SubregionServiceTest {

    @Mock private SubregionRepository subregionRepository;
    @Mock private TerritoryService territoryService;
    @Mock private PermissionService permissionService;
    @Mock private SubregionTypeRegistry typeRegistry;
    @Mock private RegionPermissionService regionPermissionService;
    @Mock private RegionTypeLimitRepository limitRepository;
    @Mock private World world;

    private SubregionService subregionService;
    private UUID playerId;
    private UUID ownerId;
    private UUID guildId;

    @BeforeEach
    void setUp() {
        subregionService = new SubregionService(subregionRepository, territoryService, permissionService, typeRegistry, regionPermissionService, limitRepository);
        playerId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        guildId = "guild-123";

        when(world.getName()).thenReturn("world");
    }

    private Location createLocation(int x, int y, int z) {
        Location loc = mock(Location.class);
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(x);
        when(loc.getBlockY()).thenReturn(y);
        when(loc.getBlockZ()).thenReturn(z);
        return loc;
    }

    @Nested
    @DisplayName("createSubregion")
    class CreateSubregion {

        @Test
        @DisplayName("should create subregion when all validations pass")
        void shouldCreateSubregionWhenAllValidationsPass() {
            Location pos1 = createLocation(0, 64, 0);
            Location pos2 = createLocation(10, 70, 10);

            when(permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS))
                    .thenReturn(true);
            when(subregionRepository.findByGuildAndName(guildId, "TestRegion"))
                    .thenReturn(Optional.empty());

            // Mock chunk ownership - all chunks owned by this guild
            Guild guild = mock(Guild.class);
            when(guild.getId()).thenReturn(guildId);
            when(territoryService.getChunkOwner(any(ChunkKey.class))).thenReturn(guild);

            SubregionService.SubregionCreationResult result = subregionService.createSubregion(
                    guildId, playerId, "TestRegion", pos1, pos2);

            assertThat(result.success()).isTrue();
            assertThat(result.region()).isNotNull();
            assertThat(result.region().getName()).isEqualTo("TestRegion");
            verify(subregionRepository).save(any(Subregion.class));
        }

        @Test
        @DisplayName("should fail when player lacks permission")
        void shouldFailWhenPlayerLacksPermission() {
            Location pos1 = createLocation(0, 64, 0);
            Location pos2 = createLocation(10, 70, 10);

            when(permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS))
                    .thenReturn(false);

            SubregionService.SubregionCreationResult result = subregionService.createSubregion(
                    guildId, playerId, "TestRegion", pos1, pos2);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("permission");
            verify(subregionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should fail when name already exists")
        void shouldFailWhenNameAlreadyExists() {
            Location pos1 = createLocation(0, 64, 0);
            Location pos2 = createLocation(10, 70, 10);

            when(permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS))
                    .thenReturn(true);
            when(subregionRepository.findByGuildAndName(guildId, "ExistingRegion"))
                    .thenReturn(Optional.of(mock(Subregion.class)));

            SubregionService.SubregionCreationResult result = subregionService.createSubregion(
                    guildId, playerId, "ExistingRegion", pos1, pos2);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("already exists");
        }

        @Test
        @DisplayName("should fail when volume exceeds limit")
        void shouldFailWhenVolumeExceedsLimit() {
            Location pos1 = createLocation(0, 0, 0);
            Location pos2 = createLocation(1000, 256, 1000); // Very large

            when(permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS))
                    .thenReturn(true);
            when(subregionRepository.findByGuildAndName(guildId, "HugeRegion"))
                    .thenReturn(Optional.empty());

            subregionService.setMaxVolume(100_000);

            SubregionService.SubregionCreationResult result = subregionService.createSubregion(
                    guildId, playerId, "HugeRegion", pos1, pos2);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("volume").contains("exceeds");
        }

        @Test
        @DisplayName("should fail when chunk not owned by guild")
        void shouldFailWhenChunkNotOwnedByGuild() {
            Location pos1 = createLocation(0, 64, 0);
            Location pos2 = createLocation(10, 70, 10);

            when(permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS))
                    .thenReturn(true);
            when(subregionRepository.findByGuildAndName(guildId, "TestRegion"))
                    .thenReturn(Optional.empty());
            when(territoryService.getChunkOwner(any(ChunkKey.class))).thenReturn(null);

            SubregionService.SubregionCreationResult result = subregionService.createSubregion(
                    guildId, playerId, "TestRegion", pos1, pos2);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("chunk");
        }

        @Test
        @DisplayName("should fail when positions in different worlds")
        void shouldFailWhenPositionsInDifferentWorlds() {
            Location pos1 = createLocation(0, 64, 0);

            World otherWorld = mock(World.class);
            when(otherWorld.getName()).thenReturn("other_world");
            Location pos2 = mock(Location.class);
            when(pos2.getWorld()).thenReturn(otherWorld);
            when(pos2.getBlockX()).thenReturn(10);
            when(pos2.getBlockY()).thenReturn(70);
            when(pos2.getBlockZ()).thenReturn(10);

            when(permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS))
                    .thenReturn(true);

            SubregionService.SubregionCreationResult result = subregionService.createSubregion(
                    guildId, playerId, "TestRegion", pos1, pos2);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("same world");
        }

        @Test
        @DisplayName("should fail when type is invalid")
        void shouldFailWhenTypeIsInvalid() {
            Location pos1 = createLocation(0, 64, 0);
            Location pos2 = createLocation(10, 70, 10);

            when(permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS))
                    .thenReturn(true);
            when(typeRegistry.isRegistered("invalid_type")).thenReturn(false);

            SubregionService.SubregionCreationResult result = subregionService.createSubregion(
                    guildId, playerId, "TestRegion", pos1, pos2, "invalid_type");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Unknown region type");
        }

        @Test
        @DisplayName("should fail when type volume limit exceeded")
        void shouldFailWhenTypeVolumeLimitExceeded() {
            Location pos1 = createLocation(0, 64, 0);
            Location pos2 = createLocation(10, 70, 10); // 11 * 7 * 11 = 847 blocks

            when(permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS))
                    .thenReturn(true);
            when(typeRegistry.isRegistered("farm")).thenReturn(true);
            when(limitRepository.findByTypeId("farm"))
                    .thenReturn(Optional.of(new RegionTypeLimit("farm", 500)));
            when(subregionRepository.getTotalVolumeByGuildAndType(guildId, "farm"))
                    .thenReturn(100L); // 100 existing + 847 new = 947 > 500

            SubregionService.SubregionCreationResult result = subregionService.createSubregion(
                    guildId, playerId, "TestFarm", pos1, pos2, "farm");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Exceeds").contains("limit");
        }

        @Test
        @DisplayName("should succeed when type volume within limit")
        void shouldSucceedWhenTypeVolumeWithinLimit() {
            Location pos1 = createLocation(0, 64, 0);
            Location pos2 = createLocation(4, 66, 4); // 5 * 3 * 5 = 75 blocks

            when(permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS))
                    .thenReturn(true);
            when(typeRegistry.isRegistered("farm")).thenReturn(true);
            when(limitRepository.findByTypeId("farm"))
                    .thenReturn(Optional.of(new RegionTypeLimit("farm", 500)));
            when(subregionRepository.getTotalVolumeByGuildAndType(guildId, "farm"))
                    .thenReturn(100L); // 100 existing + 75 new = 175 <= 500
            when(subregionRepository.findByGuildAndName(guildId, "TestFarm"))
                    .thenReturn(Optional.empty());

            // Mock chunk ownership
            Guild guild = mock(Guild.class);
            when(guild.getId()).thenReturn(guildId);
            when(territoryService.getChunkOwner(any(ChunkKey.class))).thenReturn(guild);

            SubregionService.SubregionCreationResult result = subregionService.createSubregion(
                    guildId, playerId, "TestFarm", pos1, pos2, "farm");

            assertThat(result.success()).isTrue();
            verify(subregionRepository).save(any(Subregion.class));
        }
    }

    @Nested
    @DisplayName("deleteSubregion")
    class DeleteSubregion {

        @Test
        @DisplayName("should delete when player has permission")
        void shouldDeleteWhenPlayerHasPermission() {
            Subregion region = mock(Subregion.class);
            when(region.getId()).thenReturn("region-123");
            when(region.isOwner(playerId)).thenReturn(false);

            when(subregionRepository.findByGuildAndName(guildId, "TestRegion"))
                    .thenReturn(Optional.of(region));
            when(permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS))
                    .thenReturn(true);

            boolean result = subregionService.deleteSubregion(guildId, playerId, "TestRegion");

            assertThat(result).isTrue();
            verify(subregionRepository).delete("region-123");
        }

        @Test
        @DisplayName("should delete when player is region owner")
        void shouldDeleteWhenPlayerIsRegionOwner() {
            Subregion region = mock(Subregion.class);
            when(region.getId()).thenReturn("region-123");
            when(region.isOwner(playerId)).thenReturn(true);

            when(subregionRepository.findByGuildAndName(guildId, "TestRegion"))
                    .thenReturn(Optional.of(region));
            when(permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS))
                    .thenReturn(false);

            boolean result = subregionService.deleteSubregion(guildId, playerId, "TestRegion");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should fail when region not found")
        void shouldFailWhenRegionNotFound() {
            when(subregionRepository.findByGuildAndName(guildId, "NonExistent"))
                    .thenReturn(Optional.empty());

            boolean result = subregionService.deleteSubregion(guildId, playerId, "NonExistent");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should fail when lacking permission and not owner")
        void shouldFailWhenLackingPermissionAndNotOwner() {
            Subregion region = mock(Subregion.class);
            when(region.isOwner(playerId)).thenReturn(false);

            when(subregionRepository.findByGuildAndName(guildId, "TestRegion"))
                    .thenReturn(Optional.of(region));
            when(permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS))
                    .thenReturn(false);

            boolean result = subregionService.deleteSubregion(guildId, playerId, "TestRegion");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("hasSubregionPermission")
    class HasSubregionPermission {

        @Test
        @DisplayName("should return true for guild owner")
        void shouldReturnTrueForGuildOwner() {
            Subregion region = mock(Subregion.class);
            when(region.getGuildId()).thenReturn(guildId);

            when(regionPermissionService.hasPermission(region, playerId, GuildPermission.BUILD))
                    .thenReturn(true);

            boolean result = subregionService.hasSubregionPermission(
                    region, playerId, GuildPermission.BUILD);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for region owner")
        void shouldReturnTrueForRegionOwner() {
            Subregion region = mock(Subregion.class);
            when(region.getGuildId()).thenReturn(guildId);
            when(region.isOwner(playerId)).thenReturn(true);

            when(regionPermissionService.hasPermission(region, playerId, GuildPermission.BUILD))
                    .thenReturn(true);

            boolean result = subregionService.hasSubregionPermission(
                    region, playerId, GuildPermission.BUILD);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should check explicit region permissions")
        void shouldCheckExplicitRegionPermissions() {
            Subregion region = mock(Subregion.class);
            when(region.getGuildId()).thenReturn(guildId);
            when(region.isOwner(playerId)).thenReturn(false);
            when(region.getPermissions()).thenReturn(GuildPermission.BUILD.getBit());

            when(regionPermissionService.hasPermission(region, playerId, GuildPermission.BUILD))
                    .thenReturn(true);
            when(regionPermissionService.hasPermission(region, playerId, GuildPermission.DESTROY))
                    .thenReturn(false);

            boolean hasBuild = subregionService.hasSubregionPermission(
                    region, playerId, GuildPermission.BUILD);
            boolean hasDestroy = subregionService.hasSubregionPermission(
                    region, playerId, GuildPermission.DESTROY);

            assertThat(hasBuild).isTrue();
            assertThat(hasDestroy).isFalse();
        }

        @Test
        @DisplayName("should fall back to guild permissions when no region permissions")
        void shouldFallBackToGuildPermissions() {
            Subregion region = mock(Subregion.class);
            when(region.getGuildId()).thenReturn(guildId);
            when(region.isOwner(playerId)).thenReturn(false);
            when(region.getPermissions()).thenReturn(0); // No explicit permissions

            when(regionPermissionService.hasPermission(region, playerId, GuildPermission.BUILD))
                    .thenReturn(true);

            boolean result = subregionService.hasSubregionPermission(
                    region, playerId, GuildPermission.BUILD);

            assertThat(result).isTrue();
            verify(regionPermissionService).hasPermission(region, playerId, GuildPermission.BUILD);
        }
    }

    @Nested
    @DisplayName("Query Methods")
    class QueryMethods {

        @Test
        @DisplayName("should get subregion at location")
        void shouldGetSubregionAtLocation() {
            Location loc = createLocation(5, 65, 5);
            Subregion region = mock(Subregion.class);

            when(subregionRepository.findByLocation(loc)).thenReturn(List.of(region));

            Optional<Subregion> result = subregionService.getSubregionAt(loc);

            assertThat(result).contains(region);
        }

        @Test
        @DisplayName("should return empty when no subregion at location")
        void shouldReturnEmptyWhenNoSubregionAtLocation() {
            Location loc = createLocation(5, 65, 5);

            when(subregionRepository.findByLocation(loc)).thenReturn(List.of());

            Optional<Subregion> result = subregionService.getSubregionAt(loc);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should get all guild subregions")
        void shouldGetAllGuildSubregions() {
            List<Subregion> regions = List.of(mock(Subregion.class), mock(Subregion.class));
            when(subregionRepository.findByGuild(guildId)).thenReturn(regions);

            List<Subregion> result = subregionService.getGuildSubregions(guildId);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should get subregions in chunk")
        void shouldGetSubregionsInChunk() {
            ChunkKey chunk = new ChunkKey("world", 0, 0);
            List<Subregion> regions = List.of(mock(Subregion.class));

            when(subregionRepository.findOverlappingChunk(chunk)).thenReturn(regions);

            List<Subregion> result = subregionService.getSubregionsInChunk(chunk);

            assertThat(result).hasSize(1);
        }
    }
}
