package org.aincraft;

import java.util.Objects;

/**
 * Wrapper class for guild member permission bitfields.
 * Provides type-safe operations on permission bits.
 */
public final class MemberPermissions {
    private int bitfield;

    private MemberPermissions(int bitfield) {
        this.bitfield = bitfield;
    }

    /**
     * Creates a MemberPermissions from a raw bitfield value.
     */
    public static MemberPermissions fromBitfield(int bitfield) {
        return new MemberPermissions(bitfield);
    }

    /**
     * Creates default permissions for new members (BUILD, DESTROY, INTERACT).
     */
    public static MemberPermissions getDefault() {
        return new MemberPermissions(GuildPermission.defaultPermissions());
    }

    /**
     * Creates permissions with all flags enabled (for owners).
     */
    public static MemberPermissions all() {
        return new MemberPermissions(GuildPermission.all());
    }

    /**
     * Creates permissions with no flags enabled.
     */
    public static MemberPermissions none() {
        return new MemberPermissions(0);
    }

    /**
     * Checks if a specific permission is granted.
     */
    public boolean has(GuildPermission permission) {
        Objects.requireNonNull(permission, "Permission cannot be null");
        return (bitfield & permission.getBit()) != 0;
    }

    /**
     * Grants a permission.
     */
    public MemberPermissions grant(GuildPermission permission) {
        Objects.requireNonNull(permission, "Permission cannot be null");
        bitfield |= permission.getBit();
        return this;
    }

    /**
     * Revokes a permission.
     */
    public MemberPermissions revoke(GuildPermission permission) {
        Objects.requireNonNull(permission, "Permission cannot be null");
        bitfield &= ~permission.getBit();
        return this;
    }

    /**
     * Returns the raw bitfield value for storage.
     */
    public int getBitfield() {
        return bitfield;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberPermissions that)) return false;
        return bitfield == that.bitfield;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(bitfield);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MemberPermissions[");
        boolean first = true;
        for (GuildPermission perm : GuildPermission.values()) {
            if (has(perm)) {
                if (!first) sb.append(", ");
                sb.append(perm.name());
                first = false;
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
