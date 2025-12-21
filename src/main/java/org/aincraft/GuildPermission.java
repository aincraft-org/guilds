package org.aincraft;

/**
 * Bitfield-based permission flags for guild members.
 * Each permission is a power of 2, enabling bitwise operations.
 */
public enum GuildPermission {
    BUILD(1),          // 1
    DESTROY(1 << 1),        // 2
    INTERACT(1 << 2),       // 4
    CLAIM(1 << 3),          // 8
    UNCLAIM(1 << 4),        // 16
    INVITE(1 << 5),         // 32
    KICK(1 << 6),           // 64
    MANAGE_ROLES(1 << 7),   // 128
    MANAGE_REGIONS(1 << 8), // 256
    VAULT_DEPOSIT(1 << 9),  // 512
    VAULT_WITHDRAW(1 << 10), // 1024
    MANAGE_SPAWN(1 << 11),   // 2048
    CREATE_ROLE(1 << 12),    // 4096
    DELETE_ROLE(1 << 13),    // 8192
    EDIT_ROLE(1 << 14),      // 16384
    ADMIN(1 << 15),          // 32768
    VIEW_LOGS(1 << 16),      // 65536
    EDIT_GUILD_INFO(1 << 17), // 131072
    CHAT_GUILD(1 << 18),     // 262144
    LEVEL_UP(1 << 19),       // 524288
    MANAGE_PROJECTS(1 << 20), // 1048576
    MANAGE_SKILLS(1 << 21),   // 2097152
    CHAT_OFFICER(1 << 22),    // 4194304
    UNCLAIM_ALL(1 << 23);      // 8388608

    private final int bit;

    GuildPermission(int bit) {
        this.bit = bit;
    }

    public int getBit() {
        return bit;
    }

    /**
     * Returns a bitfield with all permissions enabled.
     */
    public static int all() {
        int result = 0;
        for (GuildPermission perm : values()) {
            result |= perm.bit;
        }
        return result;
    }

    /**
     * Returns the default permission bitfield for new members.
     * No permissions by default.
     */
    public static int defaultPermissions() {
        return 0;
    }
}
