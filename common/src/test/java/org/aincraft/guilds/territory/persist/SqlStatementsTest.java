package org.aincraft.guilds.territory.persist;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlStatementsTest {
    @Test
    void loadsClasspathStatementWithoutSchemaQualifier() {
        assertEquals("SELECT doc FROM territories ORDER BY id",
                SqlStatements.load("territory/select.sql"));
        assertEquals("DELETE FROM territories",
                SqlStatements.load("territory/delete.sql"));
    }

    @Test
    void replacesSchemaPlaceholder() {
        assertEquals("SELECT doc FROM guilds.territories ORDER BY id",
                SqlStatements.load("territory/select.sql", "guilds"));
    }

    @Test
    void interpolatesTableIdentifiers() {
        String sql = SqlStatements.load("dialect/postgres/document-upsert.sql",
                Map.of("table", "territories", "keyColumn", "id"));
        assertEquals(
                "INSERT INTO territories (id, doc) VALUES (?, ?::jsonb) ON CONFLICT (id) DO UPDATE SET doc = EXCLUDED.doc",
                sql);
    }

    @Test
    void interpolatesSchemaAndTableTogether() {
        String sql = SqlStatements.load("dialect/postgres/singleton-upsert.sql",
                Map.of("schema", "guilds", "table", "upkeep_state", "idColumn", "id"));
        assertEquals(
                "INSERT INTO guilds.upkeep_state (id, doc) VALUES (1, ?::jsonb) ON CONFLICT (id) DO UPDATE SET doc = EXCLUDED.doc",
                sql);
    }

    @Test
    void missingResourceFailsLoudly() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> SqlStatements.load("missing.sql"));
        assertTrue(failure.getMessage().contains("/sql/missing.sql"));
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class, () -> SqlStatements.load("../secret.sql"));
        assertThrows(IllegalArgumentException.class, () -> SqlStatements.load("/sql/territory/select.sql"));
    }
}
