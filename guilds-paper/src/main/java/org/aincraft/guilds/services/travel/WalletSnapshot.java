package org.aincraft.guilds.services.travel;

import java.util.Objects;
import java.util.UUID;

/** Durable point-in-time view of one player's personal travel wallet. */
public record WalletSnapshot(UUID playerId, long balance) {
    public WalletSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        if (balance < 0L) {
            throw new IllegalArgumentException("wallet balance cannot be negative");
        }
    }
    public UUID playerUuid() {
        return playerId;
    }
}
