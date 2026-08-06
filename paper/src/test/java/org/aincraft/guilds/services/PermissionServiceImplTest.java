package org.aincraft.guilds.services;

import org.aincraft.guilds.GuildsServiceTestFixture;
import org.aincraft.guilds.models.GuildPermission;
import org.aincraft.guilds.models.Permission;
import org.aincraft.guilds.models.PermissionSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-SQLite tests for the permission store: single-flag revocation, blank
 * revocation (reset), setPermissionSet replacement, the grant(value=false)
 * clear path, and the read cache's hit/miss/invalidation contract.
 */
class PermissionServiceImplTest {

    @TempDir
    Path tempDir;

    private GuildsServiceTestFixture.Services services;
    private PermissionService permissions;
    private UUID resident;

    @BeforeEach
    void setUp() {
        services = GuildsServiceTestFixture.create(tempDir);
        permissions = services.permissionService();
        resident = UUID.randomUUID();
        // permissions.granted_by_uuid references residents(uuid) — needed by grantPlotPermission.
        services.residentService().createResident(resident, "alice");
    }

    @AfterEach
    void tearDown() {
        services.databaseManager().shutdown();
    }

    private int flags() {
        return permissions.getResidentPermissions(resident, "town", "alpha")
                .stream().mapToInt(Permission::getFlags).sum();
    }

    @Test
    void revokePermission_clearsOnlyTheNamedFlag() {
        permissions.grantPermission(resident, "build", "town", "alpha", true);
        permissions.grantPermission(resident, "destroy", "town", "alpha", true);
        assertTrue(flags() > 0);

        assertTrue(permissions.revokePermission(resident, "build", "town", "alpha"));

        int remaining = flags();
        assertEquals(GuildPermission.DESTROY.getLegacyBitwiseValue(), remaining,
                "unrelated destroy flag must survive the build revocation");
    }

    @Test
    void revokePermission_dropsRowWhenNoFlagsRemain() {
        permissions.grantPermission(resident, "build", "town", "alpha", true);
        assertTrue(permissions.revokePermission(resident, "build", "town", "alpha"));
        assertTrue(permissions.getResidentPermissions(resident, "town", "alpha").isEmpty(),
                "row with zero flags must be deleted");
    }

    @Test
    void revokePermission_blankClearsEveryFlagInContext() {
        permissions.grantPermission(resident, "build", "town", "alpha", true);
        permissions.grantPermission(resident, "destroy", "town", "alpha", true);
        assertTrue(permissions.revokePermission(resident, "", "town", "alpha"));
        assertTrue(permissions.getResidentPermissions(resident, "town", "alpha").isEmpty());
    }

    @Test
    void revokePermission_unknownPermissionOrMissingRowIsNoOp() {
        permissions.grantPermission(resident, "build", "town", "alpha", true);
        assertFalse(permissions.revokePermission(resident, "nonexistent", "town", "alpha"));
        assertFalse(permissions.revokePermission(resident, "build", "town", "other"));
    }

    @Test
    void grantWithFalseClearsTheFlag() {
        permissions.grantPermission(resident, "build", "town", "alpha", true);
        permissions.grantPermission(resident, "destroy", "town", "alpha", true);

        permissions.grantPermission(resident, "build", "town", "alpha", false);

        assertEquals(GuildPermission.DESTROY.getLegacyBitwiseValue(), flags(),
                "grant(value=false) must clear the named flag, not keep it");
    }

    @Test
    void setPermissionSetReplacesPreviouslyGrantedFlags() {
        permissions.grantPermission(resident, "build", "town", "alpha", true);
        permissions.grantPermission(resident, "destroy", "town", "alpha", true);

        PermissionSet replacement = new PermissionSet();
        replacement.grantPermission(GuildPermission.INVITE);
        assertTrue(permissions.setPermissionSet(resident, replacement, "town", "alpha"));

        List<Permission> after = permissions.getResidentPermissions(resident, "town", "alpha");
        assertEquals(1, after.size());
        assertEquals(GuildPermission.INVITE.getLegacyBitwiseValue(), after.get(0).getFlags(),
                "stale flags must be dropped when the permission set is replaced");
    }

    @Test
    void cacheHitsMissesAndInvalidationAreReported() {
        permissions.getResidentPermissions(resident, "town", "alpha");
        permissions.getResidentPermissions(resident, "town", "alpha");
        String afterTwoReads = permissions.getCacheStatistics();
        assertTrue(afterTwoReads.contains("1 misses"), afterTwoReads);
        assertTrue(afterTwoReads.contains("1 hits"), afterTwoReads);

        permissions.grantPermission(resident, "build", "town", "alpha", true);
        permissions.getResidentPermissions(resident, "town", "alpha");
        assertTrue(permissions.getCacheStatistics().contains("2 misses"),
                "grant must invalidate the cache so the next read misses");
    }

    @Test
    void failedLoadIsNotCached() throws Exception {
        permissions.grantPermission(resident, "build", "town", "alpha", true);

        // Force a load failure: drop the permissions table, read (error -> empty),
        // then recreate the table.
        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE permissions");
        }
        try {
            assertTrue(permissions.getResidentPermissions(resident, "town", "alpha").isEmpty(),
                    "a failed load returns empty for this call");
        } finally {
            try (Connection connection = services.databaseManager().getDataSource().getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS permissions (
                            id TEXT PRIMARY KEY,
                            context TEXT NOT NULL,
                            context_id TEXT NOT NULL,
                            target_type TEXT NOT NULL,
                            target_id TEXT,
                            permissions_flags INTEGER NOT NULL,
                            granted_at TEXT NOT NULL,
                            granted_by_uuid TEXT,
                            FOREIGN KEY (granted_by_uuid) REFERENCES residents(uuid) ON DELETE SET NULL
                        )
                        """);
            }
        }

        // Seed a row behind the service's back (no cache invalidation).
        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO permissions (id, context, context_id, target_type, target_id, permissions_flags, granted_at) "
                             + "VALUES (?, 'town', 'alpha', 'resident', ?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, resident.toString());
            statement.setInt(3, GuildPermission.BUILD.getLegacyBitwiseValue());
            statement.setString(4, "2026-01-01 00:00:00");
            statement.executeUpdate();
        }

        List<Permission> after = permissions.getResidentPermissions(resident, "town", "alpha");
        assertEquals(1, after.size(),
                "the failed load must not have been cached; the next read sees the new row");
    }

    @Test
    void plotPermissionMutationInvalidatesCache() {
        UUID plot = UUID.randomUUID();
        permissions.getResidentPermissions(resident, "plot", plot.toString());

        assertTrue(services.plotService().grantPlotPermission(
                plot, "resident", resident.toString(), GuildPermission.BUILD.getLegacyBitwiseValue(), resident));

        List<Permission> after = permissions.getResidentPermissions(resident, "plot", plot.toString());
        assertTrue(after.stream().anyMatch(p -> p.hasFlag(GuildPermission.BUILD.getLegacyBitwiseValue())),
                "plot-permission grant must be visible on the next read (cache invalidated)");
        assertTrue(permissions.getCacheStatistics().contains("2 misses"),
                "plot-permission mutation must invalidate the read cache");
    }
}
