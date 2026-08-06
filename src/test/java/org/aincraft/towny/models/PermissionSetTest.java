package org.aincraft.towny.models;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionSetTest {

    @Test
    void emptyLegacyFlagsCreateAnEmptyPermissionSet() {
        PermissionSet permissions = PermissionSet.fromLegacyFlags(0);

        assertTrue(permissions.copy().isEmpty());
        assertEquals(0, permissions.toLegacyFlags());
    }

    @Test
    void combinedLegacyFlagsRoundTripWithoutLosingPermissions() {
        int flags = TownyPermission.BUILD.getLegacyBitwiseValue()
                | TownyPermission.SWITCH.getLegacyBitwiseValue()
                | TownyPermission.ADMIN.getLegacyBitwiseValue();

        PermissionSet permissions = PermissionSet.fromLegacyFlags(flags);

        assertEquals(Set.of(TownyPermission.BUILD, TownyPermission.SWITCH, TownyPermission.ADMIN),
                permissions.getGrantedPermissions());
        assertEquals(flags, permissions.toLegacyFlags());
    }
}
