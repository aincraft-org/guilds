package org.aincraft.project;

public record ActiveBuff(
        String id,
        String guildId,
        String projectDefinitionId,
        BuffCategory category,
        double value,
        long activatedAt,
        long expiresAt
) {
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public long getRemainingMillis() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }
}
