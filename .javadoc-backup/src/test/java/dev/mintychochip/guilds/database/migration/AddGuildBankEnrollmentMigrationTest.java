package dev.mintychochip.guilds.database.migration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for add guild bank enrollment migration. */
class AddGuildBankEnrollmentMigrationTest {
    /** Performs the migration has version21and expected contract operation. */
    @Test
    void migration_hasVersion21AndExpectedContract() {
        AddGuildBankEnrollmentMigration migration = new AddGuildBankEnrollmentMigration();
        assertEquals(21, migration.getVersion());
        assertTrue(migration.getDescription().toLowerCase().contains("enrollment"));
        assertTrue(Modifier.isPublic(AddGuildBankEnrollmentMigration.class.getModifiers()));
    }
}
