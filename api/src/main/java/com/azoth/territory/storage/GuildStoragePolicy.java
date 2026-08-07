package com.azoth.territory.storage;

import java.util.Objects;

/** Rank thresholds controlling who may deposit, withdraw, and manage a guild's storage. */
public record GuildStoragePolicy(
        StorageRank depositRank,
        StorageRank withdrawRank,
        StorageRank manageRank
) {

    public GuildStoragePolicy {
        Objects.requireNonNull(depositRank, "depositRank");
        Objects.requireNonNull(withdrawRank, "withdrawRank");
        Objects.requireNonNull(manageRank, "manageRank");
    }

    /** Default thresholds: members deposit, assistants withdraw, the mayor manages. */
    public static GuildStoragePolicy defaults() {
        return new GuildStoragePolicy(
                StorageRank.MEMBER,
                StorageRank.ASSISTANT,
                StorageRank.MAYOR);
    }
}
