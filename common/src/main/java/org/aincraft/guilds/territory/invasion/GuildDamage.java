package org.aincraft.guilds.territory.invasion;

public record GuildDamage(long destroyedBlocks, int percent) {
    public GuildDamage {
        if (destroyedBlocks < 0) throw new IllegalArgumentException("destroyedBlocks must be non-negative");
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("percent must be between 0 and 100");
    }
}
