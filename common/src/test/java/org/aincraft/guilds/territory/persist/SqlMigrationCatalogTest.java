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
        assertEquals(22, migrations.getLast().version());
        assertTrue(migrations.stream().anyMatch(migration -> migration.version() == 16));
        SqlMigration lastOnline = migrations.stream()
                .filter(migration -> migration.version() == 20)
                .findFirst()
                .orElseThrow();
        assertEquals("last-online", lastOnline.slug());
        assertTrue(SqlStatements.load(migrations.getFirst().resource()).toUpperCase().contains("CREATE TABLE"));
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
