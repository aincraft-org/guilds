package org.aincraft.guilds.territory.persist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptsTest {
    @Test
    void checksumIsStableForGuildsInitialSchema() {
        String checksum = SqlScripts.checksum("migrations/guilds/V1__initial.sql");
        assertEquals(64, checksum.length());
        assertEquals(checksum, SqlScripts.checksum("migrations/guilds/V1__initial.sql"));
    }

    @Test
    void persistIncludesResolveCreateStatements() {
        String mysql = SqlScripts.resolve("migrations/persist/mysql/V1__document-stores.sql");
        String postgres = SqlScripts.resolve("migrations/persist/postgres/V1__document-stores.sql");
        assertTrue(mysql.contains("CREATE TABLE"));
        assertTrue(postgres.contains("JSONB"));
        assertTrue(mysql.contains("JSON"));
    }
}
