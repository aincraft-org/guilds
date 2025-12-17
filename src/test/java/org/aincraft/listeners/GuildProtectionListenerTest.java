package org.aincraft.listeners;

import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildService;
import org.aincraft.RelationshipService;
import org.aincraft.RelationType;
import org.aincraft.GuildDefaultPermissionsService;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GuildProtectionListener.
 * Tests block protection based on guild claims and permissions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GuildProtectionListener")
class GuildProtectionListenerTest {

    @Mock private GuildService guildService;
    @Mock private SubregionService subregionService;
    @Mock private RelationshipService relationshipService;
    @Mock private GuildDefaultPermissionsService guildDefaultPermissionsService;
    @Mock private Player player;
    @Mock private Block block;
    @Mock private Location location;
    @Mock private Chunk chunk;
    @Mock private World world;

    private GuildProtectionListener listener;
    private UUID playerId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        listener = new GuildProtectionListener(guildService, subregionService, relationshipService, guildDefaultPermissionsService);
        playerId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        // Default mock behavior
        when(block.getLocation()).thenReturn(location);
        when(location.getChunk()).thenReturn(chunk);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);
        when(player.getUniqueId()).thenReturn(playerId);
    }

    private ChunkKey createChunkKey() {
        return new ChunkKey("world", 0, 0);
    }

    @Nested
    @DisplayName("onBlockBreak")
    class OnBlockBreak {

        @Test
        @DisplayName("should allow breaking in unclaimed chunks")
        void shouldAllowBreakingInUnclaimedChunks() {
            when(guildService.getChunkOwner(any(ChunkKey.class))).thenReturn(null);

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("should deny non-guild member in claimed chunk")
        void shouldDenyNonGuildMemberInClaimedChunk() {
            Guild ownerGuild = new Guild("OwnerGuild", null, ownerId);
            when(guildService.getChunkOwner(any(ChunkKey.class))).thenReturn(ownerGuild);
            when(guildService.getPlayerGuild(playerId)).thenReturn(null);

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isTrue();
        }


        @Test
        @DisplayName("should allow guild member with permission")
        void shouldAllowGuildMemberWithPermission() {
            Guild guild = new Guild("TestGuild", null, ownerId);
            guild.joinGuild(playerId);

            when(guildService.getChunkOwner(any(ChunkKey.class))).thenReturn(guild);
            when(guildService.getPlayerGuild(playerId)).thenReturn(guild);
            when(subregionService.getSubregionAt(location)).thenReturn(Optional.empty());
            when(guildService.hasPermission(guild.getId(), playerId, GuildPermission.DESTROY))
                    .thenReturn(true);

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("should deny guild member without permission")
        void shouldDenyGuildMemberWithoutPermission() {
            Guild guild = new Guild("TestGuild", null, ownerId);
            guild.joinGuild(playerId);

            when(guildService.getChunkOwner(any(ChunkKey.class))).thenReturn(guild);
            when(guildService.getPlayerGuild(playerId)).thenReturn(guild);
            when(subregionService.getSubregionAt(location)).thenReturn(Optional.empty());
            when(guildService.hasPermission(guild.getId(), playerId, GuildPermission.DESTROY))
                    .thenReturn(false);

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("should check subregion permission first")
        void shouldCheckSubregionPermissionFirst() {
            Guild guild = new Guild("TestGuild", null, ownerId);
            guild.joinGuild(playerId);
            Subregion subregion = mock(Subregion.class);

            when(guildService.getChunkOwner(any(ChunkKey.class))).thenReturn(guild);
            when(guildService.getPlayerGuild(playerId)).thenReturn(guild);
            when(subregionService.getSubregionAt(location)).thenReturn(Optional.of(subregion));
            when(subregionService.hasSubregionPermission(subregion, playerId, GuildPermission.DESTROY))
                    .thenReturn(true);

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isFalse();
            verify(guildService, never()).hasPermission(anyString(), any(), eq(GuildPermission.DESTROY));
        }
    }

    @Nested
    @DisplayName("onBlockPlace")
    class OnBlockPlace {

        @Mock private org.bukkit.inventory.ItemStack itemStack;
        @Mock private org.bukkit.block.BlockState blockState;

        @Test
        @DisplayName("should allow placing in unclaimed chunks")
        void shouldAllowPlacingInUnclaimedChunks() {
            when(guildService.getChunkOwner(any(ChunkKey.class))).thenReturn(null);

            BlockPlaceEvent event = new BlockPlaceEvent(block, blockState, block, itemStack, player, true, EquipmentSlot.HAND);
            listener.onBlockPlace(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("should check BUILD permission for placing")
        void shouldCheckBuildPermissionForPlacing() {
            Guild guild = new Guild("TestGuild", null, ownerId);
            guild.joinGuild(playerId);

            when(guildService.getChunkOwner(any(ChunkKey.class))).thenReturn(guild);
            when(guildService.getPlayerGuild(playerId)).thenReturn(guild);
            when(subregionService.getSubregionAt(location)).thenReturn(Optional.empty());
            when(guildService.hasPermission(guild.getId(), playerId, GuildPermission.BUILD))
                    .thenReturn(true);

            BlockPlaceEvent event = new BlockPlaceEvent(block, blockState, block, itemStack, player, true, EquipmentSlot.HAND);
            listener.onBlockPlace(event);

            assertThat(event.isCancelled()).isFalse();
        }
    }

    @Nested
    @DisplayName("onPlayerInteract")
    class OnPlayerInteract {

        @Test
        @DisplayName("should protect chest interaction")
        void shouldProtectChestInteraction() {
            when(block.getType()).thenReturn(Material.CHEST);

            Guild guild = new Guild("TestGuild", null, ownerId);
            when(guildService.getChunkOwner(any(ChunkKey.class))).thenReturn(guild);
            when(guildService.getPlayerGuild(playerId)).thenReturn(null);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(block);
            when(event.getPlayer()).thenReturn(player);

            listener.onPlayerInteract(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("should allow non-protected block interaction")
        void shouldAllowNonProtectedBlockInteraction() {
            when(block.getType()).thenReturn(Material.STONE);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(block);

            listener.onPlayerInteract(event);

            verify(event, never()).setCancelled(anyBoolean());
        }

        @Test
        @DisplayName("should ignore null clicked block")
        void shouldIgnoreNullClickedBlock() {
            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(null);

            listener.onPlayerInteract(event);

            verify(event, never()).setCancelled(anyBoolean());
        }

        @Test
        @DisplayName("should allow interaction when player has permission")
        void shouldAllowInteractionWhenPlayerHasPermission() {
            when(block.getType()).thenReturn(Material.CHEST);

            Guild guild = new Guild("TestGuild", null, ownerId);
            guild.joinGuild(playerId);

            when(guildService.getChunkOwner(any(ChunkKey.class))).thenReturn(guild);
            when(guildService.getPlayerGuild(playerId)).thenReturn(guild);
            when(subregionService.getSubregionAt(location)).thenReturn(Optional.empty());
            when(guildService.hasPermission(guild.getId(), playerId, GuildPermission.INTERACT))
                    .thenReturn(true);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(block);
            when(event.getPlayer()).thenReturn(player);

            listener.onPlayerInteract(event);

            verify(event, never()).setCancelled(true);
        }
    }
}
