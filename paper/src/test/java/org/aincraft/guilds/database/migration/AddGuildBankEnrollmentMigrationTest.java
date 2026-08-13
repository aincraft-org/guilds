package org.aincraft.guilds.database.migration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddGuildBankEnrollmentMigrationTest {
    @Test
    void migration_hasVersion21AndExpectedContract() {
        AddGuildBankEnrollmentMigration migration = new AddGuildBankEnrollmentMigration();
        assertEquals(21, migration.getVersion());
        assertTrue(migration.getDescription().toLowerCase().contains("enrollment"));
        assertTrue(Modifier.isPublic(AddGuildBankEnrollmentMigration.class.getModifiers()));
    }
}
