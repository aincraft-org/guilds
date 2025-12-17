package org.aincraft;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Guild domain entity.
 * Tests guild creation, membership management, and ownership logic.
 */
@DisplayName("Guild")
class GuildTest {

    private UUID ownerId;
    private Guild guild;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        guild = new Guild("TestGuild", "A test guild", ownerId);
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create guild with valid parameters")
        void shouldCreateGuildWithValidParameters() {
            assertThat(guild.getName()).isEqualTo("TestGuild");
            assertThat(guild.getDescription()).isEqualTo("A test guild");
            assertThat(guild.getOwnerId()).isEqualTo(ownerId);
            assertThat(guild.getId()).isNotNull();
            assertThat(guild.getMemberCount()).isEqualTo(1);
            assertThat(guild.getMembers()).contains(ownerId);
        }

        @Test
        @DisplayName("should create guild without description")
        void shouldCreateGuildWithoutDescription() {
            Guild guildNoDesc = new Guild("NoDesc", null, ownerId);
            assertThat(guildNoDesc.getDescription()).isNull();
        }

        @Test
        @DisplayName("should throw on null name")
        void shouldThrowOnNullName() {
            assertThatThrownBy(() -> new Guild(null, "desc", ownerId))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("should throw on empty name")
        void shouldThrowOnEmptyName() {
            assertThatThrownBy(() -> new Guild("   ", "desc", ownerId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("should throw on null owner")
        void shouldThrowOnNullOwner() {
            assertThatThrownBy(() -> new Guild("Name", "desc", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Owner");
        }

        @Test
        @DisplayName("should set default max members to 100")
        void shouldSetDefaultMaxMembers() {
            assertThat(guild.getMaxMembers()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Membership")
    class Membership {

        private UUID memberId;

        @BeforeEach
        void setUp() {
            memberId = UUID.randomUUID();
        }

        @Test
        @DisplayName("should allow player to join guild")
        void shouldAllowPlayerToJoin() {
            boolean result = guild.joinGuild(memberId);

            assertThat(result).isTrue();
            assertThat(guild.getMemberCount()).isEqualTo(2);
            assertThat(guild.isMember(memberId)).isTrue();
        }

        @Test
        @DisplayName("should prevent duplicate join")
        void shouldPreventDuplicateJoin() {
            guild.joinGuild(memberId);

            boolean result = guild.joinGuild(memberId);

            assertThat(result).isFalse();
            assertThat(guild.getMemberCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should allow member to leave")
        void shouldAllowMemberToLeave() {
            guild.joinGuild(memberId);

            boolean result = guild.leaveGuild(memberId);

            assertThat(result).isTrue();
            assertThat(guild.isMember(memberId)).isFalse();
            assertThat(guild.getMemberCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should prevent owner from leaving")
        void shouldPreventOwnerFromLeaving() {
            boolean result = guild.leaveGuild(ownerId);

            assertThat(result).isFalse();
            assertThat(guild.isMember(ownerId)).isTrue();
        }

        @Test
        @DisplayName("should return false when non-member tries to leave")
        void shouldReturnFalseWhenNonMemberTriesToLeave() {
            boolean result = guild.leaveGuild(memberId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should prevent joining full guild")
        void shouldPreventJoiningFullGuild() {
            guild.setMaxMembers(1);

            boolean result = guild.joinGuild(memberId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return unmodifiable members list")
        void shouldReturnUnmodifiableMembersList() {
            assertThatThrownBy(() -> guild.getMembers().add(UUID.randomUUID()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Kicking")
    class Kicking {

        private UUID memberId;

        @BeforeEach
        void setUp() {
            memberId = UUID.randomUUID();
            guild.joinGuild(memberId);
        }

        @Test
        @DisplayName("should allow owner to kick member")
        void shouldAllowOwnerToKickMember() {
            boolean result = guild.kickMember(ownerId, memberId);

            assertThat(result).isTrue();
            assertThat(guild.isMember(memberId)).isFalse();
        }

        @Test
        @DisplayName("should prevent non-owner from kicking")
        void shouldPreventNonOwnerFromKicking() {
            UUID anotherMember = UUID.randomUUID();
            guild.joinGuild(anotherMember);

            boolean result = guild.kickMember(memberId, anotherMember);

            assertThat(result).isFalse();
            assertThat(guild.isMember(anotherMember)).isTrue();
        }

        @Test
        @DisplayName("should prevent kicking owner")
        void shouldPreventKickingOwner() {
            boolean result = guild.kickMember(ownerId, ownerId);

            assertThat(result).isFalse();
            assertThat(guild.isMember(ownerId)).isTrue();
        }
    }

    @Nested
    @DisplayName("Ownership")
    class Ownership {

        @Test
        @DisplayName("should identify owner correctly")
        void shouldIdentifyOwnerCorrectly() {
            assertThat(guild.isOwner(ownerId)).isTrue();
            assertThat(guild.isOwner(UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("should allow deletion only by owner")
        void shouldAllowDeletionOnlyByOwner() {
            assertThat(guild.deleteGuild(ownerId)).isTrue();
            assertThat(guild.deleteGuild(UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("should transfer ownership to member")
        void shouldTransferOwnershipToMember() {
            UUID newOwner = UUID.randomUUID();
            guild.joinGuild(newOwner);

            boolean result = guild.transferOwnership(newOwner);

            assertThat(result).isTrue();
            assertThat(guild.isOwner(newOwner)).isTrue();
            assertThat(guild.isOwner(ownerId)).isFalse();
        }

        @Test
        @DisplayName("should not transfer ownership to non-member")
        void shouldNotTransferOwnershipToNonMember() {
            UUID nonMember = UUID.randomUUID();

            boolean result = guild.transferOwnership(nonMember);

            assertThat(result).isFalse();
            assertThat(guild.isOwner(ownerId)).isTrue();
        }
    }

    @Nested
    @DisplayName("MaxMembers")
    class MaxMembers {

        @Test
        @DisplayName("should update max members")
        void shouldUpdateMaxMembers() {
            guild.setMaxMembers(50);
            assertThat(guild.getMaxMembers()).isEqualTo(50);
        }

        @Test
        @DisplayName("should throw on max members less than 1")
        void shouldThrowOnMaxMembersLessThanOne() {
            assertThatThrownBy(() -> guild.setMaxMembers(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw on max members less than current count")
        void shouldThrowOnMaxMembersLessThanCurrentCount() {
            UUID member = UUID.randomUUID();
            guild.joinGuild(member);

            assertThatThrownBy(() -> guild.setMaxMembers(1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when IDs match")
        void shouldBeEqualWhenIdsMatch() {
            Guild restored = new Guild(
                    guild.getId(),
                    "DifferentName",
                    "Different desc",
                    UUID.randomUUID(),
                    System.currentTimeMillis(),
                    50,
                    null
            );

            assertThat(guild).isEqualTo(restored);
            assertThat(guild.hashCode()).isEqualTo(restored.hashCode());
        }

        @Test
        @DisplayName("should not be equal when IDs differ")
        void shouldNotBeEqualWhenIdsDiffer() {
            Guild other = new Guild("Same", "desc", ownerId);
            assertThat(guild).isNotEqualTo(other);
        }
    }
}
