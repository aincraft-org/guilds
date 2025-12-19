package org.aincraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.config.GuildsConfig;
import org.aincraft.storage.ChunkClaimRepository;
import org.aincraft.storage.GuildMemberRepository;
import org.aincraft.storage.GuildRelationshipRepository;
import org.aincraft.storage.GuildRepository;
import org.aincraft.storage.GuildRoleRepository;
import org.aincraft.storage.InviteRepository;
import org.aincraft.storage.MemberRoleRepository;
import org.aincraft.storage.PlayerGuildMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for GuildService using Mockito for repository mocking.
 * Tests business logic for guild operations, membership, permissions, and claims.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuildService")
class GuildServiceTest {

    @Mock private GuildRepository guildRepository;
    @Mock private PlayerGuildMapping playerGuildMapping;
    @Mock private GuildMemberRepository memberRepository;
    @Mock private GuildRoleRepository roleRepository;
    @Mock private MemberRoleRepository memberRoleRepository;
    @Mock private ChunkClaimRepository chunkClaimRepository;
    @Mock private GuildRelationshipRepository relationshipRepository;
    @Mock private org.aincraft.claim.ChunkClaimLogRepository chunkClaimLogRepository;
    @Mock private InviteRepository inviteRepository;
    @Mock private org.aincraft.project.storage.GuildProjectPoolRepository poolRepository;
    @Mock private GuildsConfig config;

    private GuildService guildService;
    private UUID ownerId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        guildService = new GuildService(
                guildRepository,
                playerGuildMapping,
                memberRepository,
                roleRepository,
                memberRoleRepository,
                chunkClaimRepository,
                relationshipRepository,
                chunkClaimLogRepository,
                inviteRepository,
                poolRepository,
                config
        );
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("createGuild")
    class CreateGuild {

        @Test
        @DisplayName("should create guild successfully")
        void shouldCreateGuildSuccessfully() {
            when(guildRepository.findByName("TestGuild")).thenReturn(Optional.empty());
            when(playerGuildMapping.isPlayerInGuild(ownerId)).thenReturn(false);

            Guild result = guildService.createGuild("TestGuild", "Description", ownerId);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("TestGuild");
            verify(guildRepository).save(any(Guild.class));
            verify(playerGuildMapping).addPlayerToGuild(eq(ownerId), anyString());
            verify(memberRepository).addMember(anyString(), eq(ownerId), any(MemberPermissions.class));
            verify(roleRepository).save(any(GuildRole.class));
        }

        @Test
        @DisplayName("should return null if guild name exists")
        void shouldReturnNullIfGuildNameExists() {
            when(guildRepository.findByName("ExistingGuild")).thenReturn(Optional.of(mock(Guild.class)));

            Guild result = guildService.createGuild("ExistingGuild", "Description", ownerId);

            assertThat(result).isNull();
            verify(guildRepository, never()).save(any());
        }

        @Test
        @DisplayName("should return null if owner already in guild")
        void shouldReturnNullIfOwnerAlreadyInGuild() {
            when(guildRepository.findByName("NewGuild")).thenReturn(Optional.empty());
            when(playerGuildMapping.isPlayerInGuild(ownerId)).thenReturn(true);

            Guild result = guildService.createGuild("NewGuild", "Description", ownerId);

            assertThat(result).isNull();
            verify(guildRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw on null name")
        void shouldThrowOnNullName() {
            assertThatThrownBy(() -> guildService.createGuild(null, "desc", ownerId))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw on null owner")
        void shouldThrowOnNullOwner() {
            assertThatThrownBy(() -> guildService.createGuild("Name", "desc", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("deleteGuild")
    class DeleteGuild {

        private Guild guild;

        @BeforeEach
        void setUp() {
            guild = new Guild("TestGuild", "Description", ownerId);
            guild.joinGuild(memberId);
        }

        @Test
        @DisplayName("should delete guild when owner requests")
        void shouldDeleteGuildWhenOwnerRequests() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));

            boolean result = guildService.deleteGuild(guild.getId(), ownerId);

            assertThat(result).isTrue();
            verify(playerGuildMapping, times(2)).removePlayerFromGuild(any());
            verify(memberRepository).removeAllMembers(guild.getId());
            verify(memberRoleRepository).removeAllByGuild(guild.getId());
            verify(roleRepository).deleteAllByGuild(guild.getId());
            verify(chunkClaimRepository).unclaimAll(guild.getId());
            verify(guildRepository).delete(guild.getId());
        }

        @Test
        @DisplayName("should return false when non-owner requests deletion")
        void shouldReturnFalseWhenNonOwnerRequestsDeletion() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));

            boolean result = guildService.deleteGuild(guild.getId(), memberId);

            assertThat(result).isFalse();
            verify(guildRepository, never()).delete(anyString());
        }

        @Test
        @DisplayName("should return false when guild not found")
        void shouldReturnFalseWhenGuildNotFound() {
            when(guildRepository.findById("nonexistent")).thenReturn(Optional.empty());

            boolean result = guildService.deleteGuild("nonexistent", ownerId);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("joinGuild")
    class JoinGuild {

        private Guild guild;

        @BeforeEach
        void setUp() {
            guild = new Guild("TestGuild", "Description", ownerId);
        }

        @Test
        @DisplayName("should allow player to join guild")
        void shouldAllowPlayerToJoinGuild() {
            when(playerGuildMapping.isPlayerInGuild(memberId)).thenReturn(false);
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));
            when(roleRepository.findByGuildAndName(anyString(), eq(GuildRole.DEFAULT_ROLE_NAME)))
                    .thenReturn(Optional.of(GuildRole.createDefault(guild.getId())));

            boolean result = guildService.joinGuild(guild.getId(), memberId);

            assertThat(result).isTrue();
            verify(playerGuildMapping).addPlayerToGuild(memberId, guild.getId());
            verify(memberRepository).addMember(eq(guild.getId()), eq(memberId), any(MemberPermissions.class));
            verify(guildRepository).save(guild);
        }

        @Test
        @DisplayName("should return false if player already in a guild")
        void shouldReturnFalseIfPlayerAlreadyInGuild() {
            when(playerGuildMapping.isPlayerInGuild(memberId)).thenReturn(true);

            boolean result = guildService.joinGuild(guild.getId(), memberId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false if guild not found")
        void shouldReturnFalseIfGuildNotFound() {
            when(playerGuildMapping.isPlayerInGuild(memberId)).thenReturn(false);
            when(guildRepository.findById("nonexistent")).thenReturn(Optional.empty());

            boolean result = guildService.joinGuild("nonexistent", memberId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false if guild is full")
        void shouldReturnFalseIfGuildIsFull() {
            guild.setMaxMembers(1);
            when(playerGuildMapping.isPlayerInGuild(memberId)).thenReturn(false);
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));

            boolean result = guildService.joinGuild(guild.getId(), memberId);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("leaveGuild")
    class LeaveGuild {

        private Guild guild;

        @BeforeEach
        void setUp() {
            guild = new Guild("TestGuild", "Description", ownerId);
            guild.joinGuild(memberId);
        }

        @Test
        @DisplayName("should allow member to leave")
        void shouldAllowMemberToLeave() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));

            LeaveResult result = guildService.leaveGuild(guild.getId(), memberId);

            assertThat(result.isSuccess()).isTrue();
            verify(playerGuildMapping).removePlayerFromGuild(memberId);
            verify(memberRepository).removeMember(guild.getId(), memberId);
            verify(memberRoleRepository).removeAllMemberRoles(guild.getId(), memberId);
        }

        @Test
        @DisplayName("should prevent owner from leaving")
        void shouldPreventOwnerFromLeaving() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));

            LeaveResult result = guildService.leaveGuild(guild.getId(), ownerId);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatus()).isEqualTo(LeaveResult.Status.OWNER_CANNOT_LEAVE);
            verify(playerGuildMapping, never()).removePlayerFromGuild(any());
        }
    }

    @Nested
    @DisplayName("kickMember")
    class KickMember {

        private Guild guild;

        @BeforeEach
        void setUp() {
            guild = new Guild("TestGuild", "Description", ownerId);
            guild.joinGuild(memberId);
        }

        @Test
        @DisplayName("should allow owner to kick member")
        void shouldAllowOwnerToKickMember() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));

            boolean result = guildService.kickMember(guild.getId(), ownerId, memberId);

            assertThat(result).isTrue();
            verify(playerGuildMapping).removePlayerFromGuild(memberId);
            verify(memberRepository).removeMember(guild.getId(), memberId);
        }

        @Test
        @DisplayName("should prevent non-owner from kicking")
        void shouldPreventNonOwnerFromKicking() {
            UUID anotherMember = UUID.randomUUID();
            guild.joinGuild(anotherMember);
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));

            boolean result = guildService.kickMember(guild.getId(), memberId, anotherMember);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("hasPermission")
    class HasPermission {

        private Guild guild;

        @BeforeEach
        void setUp() {
            guild = new Guild("TestGuild", "Description", ownerId);
            guild.joinGuild(memberId);
        }

        @Test
        @DisplayName("should return true for owner regardless of permissions")
        void shouldReturnTrueForOwner() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));

            boolean result = guildService.hasPermission(guild.getId(), ownerId, GuildPermission.MANAGE_ROLES);

            assertThat(result).isTrue();
            // Owner short-circuits, no role lookup needed
            verify(memberRoleRepository, never()).getMemberRoleIds(anyString(), any());
        }

        @Test
        @DisplayName("should check role permissions for non-owner")
        void shouldCheckRolePermissionsForNonOwner() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));
            when(memberRoleRepository.getMemberRoleIds(guild.getId(), memberId)).thenReturn(List.of("role1"));

            GuildRole role = new GuildRole(guild.getId(), "Member", GuildPermission.BUILD.getBit());
            when(roleRepository.findById("role1")).thenReturn(Optional.of(role));

            boolean hasBuild = guildService.hasPermission(guild.getId(), memberId, GuildPermission.BUILD);
            boolean hasManage = guildService.hasPermission(guild.getId(), memberId, GuildPermission.MANAGE_ROLES);

            assertThat(hasBuild).isTrue();
            assertThat(hasManage).isFalse();
        }

        @Test
        @DisplayName("should return false for non-existent guild")
        void shouldReturnFalseForNonExistentGuild() {
            when(guildRepository.findById("nonexistent")).thenReturn(Optional.empty());

            boolean result = guildService.hasPermission("nonexistent", memberId, GuildPermission.BUILD);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("claimChunk")
    class ClaimChunk {

        private Guild guild;
        private ChunkKey chunk;

        @BeforeEach
        void setUp() {
            // Mock config to return default buffer distance of 4 for claim tests
            // Use lenient() since some tests exit before checking buffer
            lenient().when(config.getClaimBufferDistance()).thenReturn(4);
            // Mock empty guild list so buffer check passes (no other guilds)
            lenient().when(guildRepository.findAll()).thenReturn(Collections.emptyList());

            guild = new Guild("TestGuild", "Description", ownerId);
            chunk = new ChunkKey("world", 0, 0);
        }

        @Test
        @DisplayName("should claim chunk when player has permission")
        void shouldClaimChunkWhenPlayerHasPermission() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));
            // Set homeblock so it's not the first claim (avoids spawn setup)
            guild.setHomeblock(chunk);
            ChunkKey newChunk = new ChunkKey("world", 1, 0);
            when(chunkClaimRepository.getGuildChunks(guild.getId())).thenReturn(List.of(chunk));
            when(chunkClaimRepository.claim(newChunk, guild.getId(), ownerId)).thenReturn(true);

            ClaimResult result = guildService.claimChunk(guild.getId(), ownerId, newChunk);

            assertThat(result.isSuccess()).isTrue();
            verify(chunkClaimRepository).claim(newChunk, guild.getId(), ownerId);
        }

        @Test
        @DisplayName("should return NO_PERMISSION when player lacks permission")
        void shouldReturnNoPermissionWhenPlayerLacksPermission() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));
            when(memberRoleRepository.getMemberRoleIds(guild.getId(), memberId)).thenReturn(Collections.emptyList());

            ClaimResult result = guildService.claimChunk(guild.getId(), memberId, chunk);

            assertThat(result.getStatus()).isEqualTo(ClaimResult.Status.NO_PERMISSION);
            verify(chunkClaimRepository, never()).claim(any(), anyString(), any());
        }

        @Test
        @DisplayName("should return NOT_ADJACENT when claiming non-adjacent chunks")
        void shouldReturnNotAdjacentWhenClaimingNonAdjacentChunks() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));
            ChunkKey existingChunk = new ChunkKey("world", 0, 0);
            ChunkKey nonAdjacentChunk = new ChunkKey("world", 5, 5); // Far away
            when(chunkClaimRepository.getGuildChunks(guild.getId())).thenReturn(List.of(existingChunk));

            ClaimResult result = guildService.claimChunk(guild.getId(), ownerId, nonAdjacentChunk);

            assertThat(result.getStatus()).isEqualTo(ClaimResult.Status.NOT_ADJACENT);
            verify(chunkClaimRepository, never()).claim(any(), anyString(), any());
        }

        @Test
        @DisplayName("should allow claiming adjacent chunks")
        void shouldAllowClaimingAdjacentChunks() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));
            // Set homeblock so it's not the first claim (avoids spawn setup)
            guild.setHomeblock(chunk);
            ChunkKey existingChunk = new ChunkKey("world", 0, 0);
            ChunkKey adjacentChunk = new ChunkKey("world", 1, 0); // Adjacent to the right
            when(chunkClaimRepository.getGuildChunks(guild.getId())).thenReturn(List.of(existingChunk));
            when(chunkClaimRepository.claim(adjacentChunk, guild.getId(), ownerId)).thenReturn(true);

            ClaimResult result = guildService.claimChunk(guild.getId(), ownerId, adjacentChunk);

            assertThat(result.isSuccess()).isTrue();
            verify(chunkClaimRepository).claim(adjacentChunk, guild.getId(), ownerId);
        }

        @Test
        @DisplayName("should return LIMIT_EXCEEDED when guild reaches max chunks")
        void shouldReturnLimitExceededWhenGuildReachesMaxChunks() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));
            guild.setMaxChunks(2);
            guild.setHomeblock(chunk);

            ChunkKey chunk1 = new ChunkKey("world", 0, 0);
            ChunkKey chunk2 = new ChunkKey("world", 1, 0);
            ChunkKey chunk3 = new ChunkKey("world", 2, 0); // This will exceed limit

            // Return 2 existing chunks (already at limit)
            when(chunkClaimRepository.getGuildChunks(guild.getId())).thenReturn(List.of(chunk1, chunk2));

            ClaimResult result = guildService.claimChunk(guild.getId(), ownerId, chunk3);

            assertThat(result.getStatus()).isEqualTo(ClaimResult.Status.LIMIT_EXCEEDED);
            assertThat(result.getReason()).contains("2");
            verify(chunkClaimRepository, never()).claim(any(), anyString(), any());
        }

    }

    @Nested
    @DisplayName("unclaimChunk")
    class UnclaimChunk {

        private Guild guild;
        private ChunkKey chunk;

        @BeforeEach
        void setUp() {
            guild = new Guild("TestGuild", "Description", ownerId);
            chunk = new ChunkKey("world", 0, 0);
        }

        @Test
        @DisplayName("should unclaim chunk when player has permission")
        void shouldUnclaimChunkWhenPlayerHasPermission() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));
            when(chunkClaimRepository.unclaim(chunk, guild.getId())).thenReturn(true);

            boolean result = guildService.unclaimChunk(guild.getId(), ownerId, chunk);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Role Management")
    class RoleManagement {

        private Guild guild;

        @BeforeEach
        void setUp() {
            guild = new Guild("TestGuild", "Description", ownerId);
        }

        @Test
        @DisplayName("should create role when player has permission")
        void shouldCreateRoleWhenPlayerHasPermission() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));
            when(roleRepository.findByGuildAndName(guild.getId(), "Admin")).thenReturn(Optional.empty());

            GuildRole result = guildService.createRole(guild.getId(), ownerId, "Admin", GuildPermission.all());

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Admin");
            verify(roleRepository).save(any(GuildRole.class));
        }

        @Test
        @DisplayName("should return null if role name exists")
        void shouldReturnNullIfRoleNameExists() {
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));
            when(roleRepository.findByGuildAndName(guild.getId(), "Existing"))
                    .thenReturn(Optional.of(mock(GuildRole.class)));

            GuildRole result = guildService.createRole(guild.getId(), ownerId, "Existing", 0);

            assertThat(result).isNull();
            verify(roleRepository, never()).save(any());
        }

        @Test
        @DisplayName("should delete role when player has permission")
        void shouldDeleteRoleWhenPlayerHasPermission() {
            GuildRole role = new GuildRole(guild.getId(), "ToDelete", 0);
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));
            when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));

            boolean result = guildService.deleteRole(guild.getId(), ownerId, role.getId());

            assertThat(result).isTrue();
            verify(memberRoleRepository).removeAllByRole(role.getId());
            verify(roleRepository).delete(role.getId());
        }

        @Test
        @DisplayName("should assign role to member")
        void shouldAssignRoleToMember() {
            guild.joinGuild(memberId);
            GuildRole role = new GuildRole(guild.getId(), "Member", GuildPermission.defaultPermissions());

            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));
            when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));

            boolean result = guildService.assignRole(guild.getId(), ownerId, memberId, role.getId());

            assertThat(result).isTrue();
            verify(memberRoleRepository).assignRole(guild.getId(), memberId, role.getId());
        }
    }

    @Nested
    @DisplayName("Query Methods")
    class QueryMethods {

        @Test
        @DisplayName("should list all guilds")
        void shouldListAllGuilds() {
            List<Guild> guilds = List.of(
                    new Guild("Guild1", null, UUID.randomUUID()),
                    new Guild("Guild2", null, UUID.randomUUID())
            );
            when(guildRepository.findAll()).thenReturn(guilds);

            List<Guild> result = guildService.listAllGuilds();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should get guild by ID")
        void shouldGetGuildById() {
            Guild guild = new Guild("Test", null, ownerId);
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));

            Guild result = guildService.getGuildById(guild.getId());

            assertThat(result).isEqualTo(guild);
        }

        @Test
        @DisplayName("should get guild by name")
        void shouldGetGuildByName() {
            Guild guild = new Guild("UniqueGuild", null, ownerId);
            when(guildRepository.findByName("UniqueGuild")).thenReturn(Optional.of(guild));

            Guild result = guildService.getGuildByName("UniqueGuild");

            assertThat(result).isEqualTo(guild);
        }

        @Test
        @DisplayName("should get player guild")
        void shouldGetPlayerGuild() {
            Guild guild = new Guild("MyGuild", null, ownerId);
            when(playerGuildMapping.getPlayerGuildId(ownerId)).thenReturn(Optional.of(guild.getId()));
            when(guildRepository.findById(guild.getId())).thenReturn(Optional.of(guild));

            Guild result = guildService.getPlayerGuild(ownerId);

            assertThat(result).isEqualTo(guild);
        }

        @Test
        @DisplayName("should return null when player not in guild")
        void shouldReturnNullWhenPlayerNotInGuild() {
            when(playerGuildMapping.getPlayerGuildId(memberId)).thenReturn(Optional.empty());

            Guild result = guildService.getPlayerGuild(memberId);

            assertThat(result).isNull();
        }
    }
}
