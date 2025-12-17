package org.aincraft.claim;

import org.aincraft.ChunkKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ChunkClaimLog record.
 */
class ChunkClaimLogTest {

    @Test
    @DisplayName("should create log with all fields")
    void shouldCreateLogWithAllFields() {
        ChunkKey chunk = new ChunkKey("world", 5, -3);
        UUID playerId = UUID.randomUUID();
        String guildId = "guild123";

        ChunkClaimLog log = new ChunkClaimLog(
                1L,
                guildId,
                chunk,
                playerId,
                ChunkClaimLog.ActionType.CLAIM,
                System.currentTimeMillis()
        );

        assertThat(log.id()).isEqualTo(1L);
        assertThat(log.guildId()).isEqualTo(guildId);
        assertThat(log.chunk()).isEqualTo(chunk);
        assertThat(log.playerId()).isEqualTo(playerId);
        assertThat(log.action()).isEqualTo(ChunkClaimLog.ActionType.CLAIM);
        assertThat(log.timestamp()).isGreaterThan(0);
    }

    @Test
    @DisplayName("should create log with simplified constructor")
    void shouldCreateLogWithSimplifiedConstructor() {
        ChunkKey chunk = new ChunkKey("world", 10, 20);
        UUID playerId = UUID.randomUUID();
        String guildId = "guild456";

        ChunkClaimLog log = new ChunkClaimLog(guildId, chunk, playerId, ChunkClaimLog.ActionType.UNCLAIM);

        assertThat(log.id()).isZero();
        assertThat(log.guildId()).isEqualTo(guildId);
        assertThat(log.chunk()).isEqualTo(chunk);
        assertThat(log.playerId()).isEqualTo(playerId);
        assertThat(log.action()).isEqualTo(ChunkClaimLog.ActionType.UNCLAIM);
        assertThat(log.timestamp()).isGreaterThan(0);
    }

    @Test
    @DisplayName("should fail with null guild ID")
    void shouldFailWithNullGuildId() {
        ChunkKey chunk = new ChunkKey("world", 0, 0);
        UUID playerId = UUID.randomUUID();

        assertThatThrownBy(() -> new ChunkClaimLog(null, chunk, playerId, ChunkClaimLog.ActionType.CLAIM))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Guild ID cannot be null");
    }

    @Test
    @DisplayName("should fail with null chunk")
    void shouldFailWithNullChunk() {
        UUID playerId = UUID.randomUUID();

        assertThatThrownBy(() -> new ChunkClaimLog("guild", null, playerId, ChunkClaimLog.ActionType.CLAIM))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Chunk cannot be null");
    }

    @Test
    @DisplayName("should fail with null player ID")
    void shouldFailWithNullPlayerId() {
        ChunkKey chunk = new ChunkKey("world", 0, 0);

        assertThatThrownBy(() -> new ChunkClaimLog("guild", chunk, null, ChunkClaimLog.ActionType.CLAIM))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Player ID cannot be null");
    }

    @Test
    @DisplayName("should fail with null action")
    void shouldFailWithNullAction() {
        ChunkKey chunk = new ChunkKey("world", 0, 0);
        UUID playerId = UUID.randomUUID();

        assertThatThrownBy(() -> new ChunkClaimLog("guild", chunk, playerId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Action cannot be null");
    }

    @Test
    @DisplayName("should have CLAIM and UNCLAIM action types")
    void shouldHaveClaimAndUnclaimActionTypes() {
        assertThat(ChunkClaimLog.ActionType.values()).containsExactly(
                ChunkClaimLog.ActionType.CLAIM,
                ChunkClaimLog.ActionType.UNCLAIM
        );
    }
}
