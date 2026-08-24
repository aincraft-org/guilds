package org.aincraft.guilds.projects;

/**
 * Guild level N grants N total project skill points.
 * Unspent points are persisted separately; spent = earned - unspent.
 */
public final class GuildSkillPoints {

    private GuildSkillPoints() {
    }

    public static int totalEarned(int guildLevel) {
        return Math.max(0, guildLevel);
    }

    public static int unspentAfterLevelChange(int currentUnspent, int fromLevel, int toLevel) {
        int gained = Math.max(0, toLevel - fromLevel);
        return Math.max(0, currentUnspent) + gained;
    }

    public static int spent(int guildLevel, int unspent) {
        return Math.max(0, totalEarned(guildLevel) - Math.max(0, unspent));
    }
}
