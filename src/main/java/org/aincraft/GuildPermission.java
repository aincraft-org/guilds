package org.aincraft;

/**
 * Bitfield-based permission flags for guild members.
 * Each permission is a power of 2, enabling bitwise operations.
 */
public enum GuildPermission {
    BUILD(1 << 0),          // 1
    DESTROY(1 << 1),        // 2
    INTERACT(1 << 2),       // 4
    CLAIM(1 << 3),          // 8
    UNCLAIM(1 << 4),        // 16
    INVITE(1 << 5),         // 32
    KICK(1 << 6),           // 64
    MANAGE_ROLES(1 << 7),   // 128
    MANAGE_REGIONS(1 << 8); // 256

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
     * Includes BUILD, DESTROY, and INTERACT.
     */
    public static int defaultPermissions() {
        return BUILD.bit | DESTROY.bit | INTERACT.bit;
    }
}
