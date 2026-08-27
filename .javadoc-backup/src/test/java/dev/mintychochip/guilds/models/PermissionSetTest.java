package dev.mintychochip.guilds.models;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for permission set. */
class PermissionSetTest {

    /** Performs the empty legacy flags create an empty permission set operation. */
    @Test
    void emptyLegacyFlagsCreateAnEmptyPermissionSet() {
        PermissionSet permissions = PermissionSet.fromLegacyFlags(0);

        assertTrue(permissions.copy().isEmpty());
        assertEquals(0, permissions.toLegacyFlags());
    }

    /** Performs the combined legacy flags round trip without losing permissions operation. */
    @Test
    void combinedLegacyFlagsRoundTripWithoutLosingPermissions() {
        int flags = GuildPermission.BUILD.getLegacyBitwiseValue()
                | GuildPermission.SWITCH.getLegacyBitwiseValue()
                | GuildPermission.ADMIN.getLegacyBitwiseValue();

        PermissionSet permissions = PermissionSet.fromLegacyFlags(flags);

        assertEquals(Set.of(GuildPermission.BUILD, GuildPermission.SWITCH, GuildPermission.ADMIN),
                permissions.getGrantedPermissions());
        assertEquals(flags, permissions.toLegacyFlags());
    }

    /** Performs the every permission has aunique legacy bit operation. */
    @Test
    void everyPermissionHasAUniqueLegacyBit() {
        Set<Integer> bits = new HashSet<>();
        for (GuildPermission permission : GuildPermission.values()) {
            int bit = permission.getLegacyBitwiseValue();
            assertTrue(bits.add(bit), "Duplicate legacy bit for " + permission);
            assertTrue(Integer.bitCount(bit) == 1, "Legacy bit must be a single power of two: " + permission);
        }
    }
}
