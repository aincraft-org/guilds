package org.aincraft.guilds.services;

import org.aincraft.guilds.territory.persist.SqlStatements;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlResourceServiceTest {
    @Test
    void residentsSqlLivesOnTheClasspath() {
        assertTrue(SqlStatements.load("residents/insert.sql").toUpperCase(Locale.ROOT)
                .contains("INSERT INTO RESIDENTS"));
        assertTrue(SqlStatements.load("residents/select-by-uuid.sql").contains("WHERE uuid = ?"));
        assertTrue(SqlStatements.load("residents/update.sql").toUpperCase(Locale.ROOT)
                .contains("UPDATE RESIDENTS"));
    }

}
