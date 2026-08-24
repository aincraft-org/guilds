package org.aincraft.guilds.territory.persist;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlMigrationCatalogTest {
    @Test
    void guildsManifestLoadsIncreasingVersions() {
        List<SqlMigration> migrations = SqlMigrationCatalog.load("guilds", DatabaseType.MYSQL);
        assertEquals(1, migrations.getFirst().version());
        assertEquals("initial", migrations.getFirst().slug());
        assertEquals("migrations/guilds/V1__initial.sql", migrations.getFirst().resource());
        assertEquals(27, migrations.getLast().version());
        assertTrue(migrations.stream().anyMatch(migration -> migration.version() == 16));
        SqlMigration lastOnline = migrations.stream()
                .filter(migration -> migration.version() == 20)
                .findFirst()
                .orElseThrow();
        assertEquals("last-online", lastOnline.slug());
        assertTrue(SqlStatements.load(migrations.getFirst().resource()).toUpperCase().contains("CREATE TABLE"));
        SqlMigration storageOperations = migrations.stream()
                .filter(migration -> migration.version() == 25)
                .findFirst()
                .orElseThrow();
        assertEquals("guild-storage-operations", storageOperations.slug());
        assertEquals("migrations/guilds/V25__guild-storage-operations.sql", storageOperations.resource());
        SqlMigration storageAuditOperation = migrations.stream()
                .filter(migration -> migration.version() == 26)
                .findFirst()
                .orElseThrow();
        assertEquals("guild-storage-audit-operation", storageAuditOperation.slug());
        assertEquals(
                "migrations/guilds/V26__guild-storage-audit-operation.sql", storageAuditOperation.resource());
        SqlMigration requestSnapshot = migrations.stream()
                .filter(migration -> migration.version() == 27)
                .findFirst()
                .orElseThrow();
        assertEquals("guild-storage-operation-request-snapshot", requestSnapshot.slug());
        assertEquals(
                "migrations/guilds/V27__guild-storage-operation-request-snapshot.sql", requestSnapshot.resource());
    }

    @Test
    void persistManifestUsesDialectOverride() {
        SqlMigration mysql = SqlMigrationCatalog.load("persist", DatabaseType.MYSQL).getFirst();
        SqlMigration postgres = SqlMigrationCatalog.load("persist", DatabaseType.POSTGRESQL).getFirst();
        assertEquals("migrations/persist/mysql/V1__document-stores.sql", mysql.resource());
        assertEquals("migrations/persist/postgres/V1__document-stores.sql", postgres.resource());
        assertTrue(SqlScripts.resolve(mysql.resource()).contains("CREATE TABLE"));
        assertTrue(SqlScripts.resolve(postgres.resource()).contains("JSONB"));
    }
}
