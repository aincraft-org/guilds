package org.aincraft.commands.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.aincraft.ChunkKey;
import org.aincraft.ClaimResult;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.TerritoryService;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
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
 * Unit tests for CreateComponent command.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CreateComponent")
class CreateComponentTest {

    @Mock private GuildLifecycleService lifecycleService;
    @Mock private TerritoryService territoryService;
    @Mock private GuildService guildService;
    @Mock private Player player;
    @Mock private Location location;
    @Mock private Chunk chunk;
    @Mock private World world;

    private CreateComponent createComponent;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        createComponent = new CreateComponent(lifecycleService, territoryService, guildService);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getLocation()).thenReturn(location);
        when(location.getChunk()).thenReturn(chunk);
        when(location.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);
        when(chunk.getWorld()).thenReturn(world); // For ChunkKey.from(chunk)
        when(world.getName()).thenReturn("world");
        // Default: spawn setting succeeds
        when(guildService.setGuildSpawn(any(UUID.class), any(UUID.class), any(Location.class))).thenReturn(true);
    }

    @Test
    @DisplayName("should have correct name")
    void shouldHaveCorrectName() {
        assertThat(createComponent.getName()).isEqualTo("create");
    }

    @Test
    @DisplayName("should have correct permission")
    void shouldHaveCorrectPermission() {
        assertThat(createComponent.getPermission()).isEqualTo("guilds.create");
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("should create guild with name only")
        void shouldCreateGuildWithNameOnly() {
            when(player.hasPermission("guilds.create")).thenReturn(true);
            Guild guild = new Guild("TestGuild", null, playerId);

            // Mock validation phase
            when(territoryService.getChunkOwner(any(ChunkKey.class))).thenReturn(null);
            when(territoryService.validateBufferDistance(any(ChunkKey.class), any(UUID.class)))
                    .thenReturn(ClaimResult.success());

            // Mock creation and claim phases
            when(lifecycleService.createGuild(eq("TestGuild"), isNull(), eq(playerId)))
                    .thenReturn(guild);
            when(territoryService.claimChunk(eq(guild.getId()), eq(playerId), any(ChunkKey.class)))
                    .thenReturn(ClaimResult.success());

            boolean result = createComponent.execute(player, new String[]{"create", "TestGuild"});

            assertThat(result).isTrue();
            verify(territoryService).getChunkOwner(any(ChunkKey.class));
            verify(territoryService).validateBufferDistance(any(ChunkKey.class), any(UUID.class));
            verify(lifecycleService).createGuild("TestGuild", null, playerId);
            verify(territoryService).claimChunk(eq(guild.getId()), eq(playerId), any(ChunkKey.class));
        }

        @Test
        @DisplayName("should create guild with name and description")
        void shouldCreateGuildWithNameAndDescription() {
            when(player.hasPermission("guilds.create")).thenReturn(true);
            Guild guild = new Guild("TestGuild", "A cool guild", playerId);

            // Mock validation phase
            when(territoryService.getChunkOwner(any(ChunkKey.class))).thenReturn(null);
            when(territoryService.validateBufferDistance(any(ChunkKey.class), any(UUID.class)))
                    .thenReturn(ClaimResult.success());

            // Mock creation and claim phases
            when(lifecycleService.createGuild(eq("TestGuild"), eq("A cool guild"), eq(playerId)))
                    .thenReturn(guild);
            when(territoryService.claimChunk(eq(guild.getId()), eq(playerId), any(ChunkKey.class)))
                    .thenReturn(ClaimResult.success());

            boolean result = createComponent.execute(player,
                    new String[]{"create", "TestGuild", "A", "cool", "guild"});

            assertThat(result).isTrue();
            verify(lifecycleService).createGuild("TestGuild", "A cool guild", playerId);
            verify(territoryService).claimChunk(eq(guild.getId()), eq(playerId), any(ChunkKey.class));
        }

        @Test
        @DisplayName("should return false when missing name argument")
        void shouldReturnFalseWhenMissingNameArgument() {
            when(player.hasPermission("guilds.create")).thenReturn(true);

            boolean result = createComponent.execute(player, new String[]{"create"});

            assertThat(result).isFalse();
            verify(lifecycleService, never()).createGuild(anyString(), anyString(), any());
            verify(territoryService, never()).validateBufferDistance(any(ChunkKey.class), any(UUID.class));
        }

        @Test
        @DisplayName("should deny when player lacks permission")
        void shouldDenyWhenPlayerLacksPermission() {
            when(player.hasPermission("guilds.create")).thenReturn(false);

            boolean result = createComponent.execute(player, new String[]{"create", "TestGuild"});

            assertThat(result).isTrue(); // Command handled, but denied
            verify(lifecycleService, never()).createGuild(anyString(), anyString(), any());
            verify(territoryService, never()).validateBufferDistance(any(ChunkKey.class), any(UUID.class));
            verify(player, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
        }

        @Test
        @DisplayName("should send error when creation fails")
        void shouldSendErrorWhenCreationFails() {
            when(player.hasPermission("guilds.create")).thenReturn(true);

            // Mock validation phase
            when(territoryService.getChunkOwner(any(ChunkKey.class))).thenReturn(null);
            when(territoryService.validateBufferDistance(any(ChunkKey.class), any(UUID.class)))
                    .thenReturn(ClaimResult.success());

            // Mock guild creation failure
            when(lifecycleService.createGuild(anyString(), any(), any())).thenReturn(null);

            boolean result = createComponent.execute(player, new String[]{"create", "Duplicate"});

            assertThat(result).isTrue();
            verify(player, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
        }

        @Test
        @DisplayName("should fail when chunk is already claimed")
        void shouldFailWhenChunkIsAlreadyClaimed() {
            when(player.hasPermission("guilds.create")).thenReturn(true);
            Guild existingGuild = new Guild("ExistingGuild", null, UUID.randomUUID());

            // Mock validation phase - chunk already claimed
            when(territoryService.getChunkOwner(any(ChunkKey.class))).thenReturn(existingGuild);

            boolean result = createComponent.execute(player, new String[]{"create", "NewGuild"});

            assertThat(result).isTrue();
            verify(lifecycleService, never()).createGuild(anyString(), anyString(), any());
            verify(player, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
        }

        @Test
        @DisplayName("should fail when chunk violates buffer distance")
        void shouldFailWhenChunkViolatesBufferDistance() {
            when(player.hasPermission("guilds.create")).thenReturn(true);

            // Mock validation phase - chunk not claimed but violates buffer
            when(territoryService.getChunkOwner(any(ChunkKey.class))).thenReturn(null);
            when(territoryService.validateBufferDistance(any(ChunkKey.class), any(UUID.class)))
                    .thenReturn(ClaimResult.tooCloseToGuild("NearbyGuild", 4, 2));

            boolean result = createComponent.execute(player, new String[]{"create", "NewGuild"});

            assertThat(result).isTrue();
            verify(lifecycleService, never()).createGuild(anyString(), anyString(), any());
            verify(player, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
        }

        @Test
        @DisplayName("should set spawn location to player's location")
        void shouldSetSpawnLocationToPlayersLocation() {
            when(player.hasPermission("guilds.create")).thenReturn(true);
            Guild guild = new Guild("TestGuild", null, playerId);

            // Mock validation phase
            when(territoryService.getChunkOwner(any(ChunkKey.class))).thenReturn(null);
            when(territoryService.validateBufferDistance(any(ChunkKey.class), any(UUID.class)))
                    .thenReturn(ClaimResult.success());

            // Mock creation and claim phases
            when(lifecycleService.createGuild(eq("TestGuild"), isNull(), eq(playerId)))
                    .thenReturn(guild);
            when(territoryService.claimChunk(eq(guild.getId()), eq(playerId), any(ChunkKey.class)))
                    .thenReturn(ClaimResult.success());

            boolean result = createComponent.execute(player, new String[]{"create", "TestGuild"});

            assertThat(result).isTrue();
            // Verify spawn was set with the player's location
            verify(guildService).setGuildSpawn(eq(guild.getId()), eq(playerId), eq(location));
        }

        @Test
        @DisplayName("should send spawn set message when spawn is successfully set")
        void shouldSendSpawnSetMessage() {
            when(player.hasPermission("guilds.create")).thenReturn(true);
            Guild guild = new Guild("TestGuild", null, playerId);

            // Mock validation phase
            when(territoryService.getChunkOwner(any(ChunkKey.class))).thenReturn(null);
            when(territoryService.validateBufferDistance(any(ChunkKey.class), any(UUID.class)))
                    .thenReturn(ClaimResult.success());

            // Mock creation and claim phases
            when(lifecycleService.createGuild(eq("TestGuild"), isNull(), eq(playerId)))
                    .thenReturn(guild);
            when(territoryService.claimChunk(eq(guild.getId()), eq(playerId), any(ChunkKey.class)))
                    .thenReturn(ClaimResult.success());
            when(guildService.setGuildSpawn(eq(guild.getId()), eq(playerId), any(Location.class)))
                    .thenReturn(true);

            boolean result = createComponent.execute(player, new String[]{"create", "TestGuild"});

            assertThat(result).isTrue();
            // Verify appropriate message was sent (should mention spawn)
            verify(player, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
        }

        @Test
        @DisplayName("should send warning when spawn setting fails")
        void shouldSendWarningWhenSpawnSettingFails() {
            when(player.hasPermission("guilds.create")).thenReturn(true);
            Guild guild = new Guild("TestGuild", null, playerId);

            // Mock validation phase
            when(territoryService.getChunkOwner(any(ChunkKey.class))).thenReturn(null);
            when(territoryService.validateBufferDistance(any(ChunkKey.class), any(UUID.class)))
                    .thenReturn(ClaimResult.success());

            // Mock creation and claim phases
            when(lifecycleService.createGuild(eq("TestGuild"), isNull(), eq(playerId)))
                    .thenReturn(guild);
            when(territoryService.claimChunk(eq(guild.getId()), eq(playerId), any(ChunkKey.class)))
                    .thenReturn(ClaimResult.success());
            // Spawn setting fails
            when(guildService.setGuildSpawn(eq(guild.getId()), eq(playerId), any(Location.class)))
                    .thenReturn(false);

            boolean result = createComponent.execute(player, new String[]{"create", "TestGuild"});

            assertThat(result).isTrue();
            // Verify warning was sent when spawn setting failed
            verify(player, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }
}
