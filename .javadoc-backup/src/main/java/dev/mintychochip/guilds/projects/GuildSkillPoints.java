package dev.mintychochip.guilds.projects;

/**
 * Guild level N grants N total project skill points.
 * Unspent points are persisted separately; spent = earned - unspent.
 */
public final class GuildSkillPoints {

    /** Creates a new guild skill points instance. */
    private GuildSkillPoints() {
    }

    /**
     * Performs the total earned operation.
     * @param guildLevel the guild level
     * @return the result
     */
    public static int totalEarned(int guildLevel) {
        return Math.max(0, guildLevel);
    }

    /**
     * Performs the unspent after level change operation.
     * @param currentUnspent the current unspent
     * @param fromLevel the from level
     * @param toLevel the to level
     * @return the result
     */
    public static int unspentAfterLevelChange(int currentUnspent, int fromLevel, int toLevel) {
        int gained = Math.max(0, toLevel - fromLevel);
        return Math.max(0, currentUnspent) + gained;
    }

    /**
     * Performs the spent operation.
     * @param guildLevel the guild level
     * @param unspent the unspent
     * @return the result
     */
    public static int spent(int guildLevel, int unspent) {
        return Math.max(0, totalEarned(guildLevel) - Math.max(0, unspent));
    }
}
