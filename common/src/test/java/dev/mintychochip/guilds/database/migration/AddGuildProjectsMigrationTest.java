package dev.mintychochip.guilds.database.migration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for add guild projects migration. */
class AddGuildProjectsMigrationTest {
    /** Performs the migration has version22and active project contract operation. */
    @Test
    void migration_hasVersion22AndActiveProjectContract() {
        AddGuildProjectsMigration migration = new AddGuildProjectsMigration();
        assertEquals(22, migration.getVersion());
        assertTrue(migration.getDescription().toLowerCase(java.util.Locale.ROOT).contains("project"));
        assertTrue(Modifier.isPublic(AddGuildProjectsMigration.class.getModifiers()));
    }
}
