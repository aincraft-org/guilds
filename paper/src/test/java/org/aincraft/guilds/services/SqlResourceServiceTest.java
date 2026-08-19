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

    @Test
    void permissionsSqlLivesOnTheClasspath() {
        assertTrue(SqlStatements.load("permissions/insert.sql").toUpperCase(Locale.ROOT)
                .contains("INSERT INTO PERMISSIONS"));
        assertTrue(SqlStatements.load("permissions/select-resident-flags.sql").contains("WHERE context = ?"));
    }

    @Test
    void alliancesSqlLivesOnTheClasspath() {
        assertTrue(SqlStatements.load("alliances/insert.sql").toUpperCase(Locale.ROOT)
                .contains("INSERT INTO ALLIANCES"));
        assertTrue(SqlStatements.load("alliances/select-all.sql").contains("{membersAgg}"));
    }

    @Test
    void broadcastsSqlLivesOnTheClasspath() {
        assertTrue(SqlStatements.load("broadcasts/insert.sql").toUpperCase(Locale.ROOT)
                .contains("INSERT INTO BROADCAST_MESSAGES"));
        assertTrue(SqlStatements.load("broadcasts/select-for-player.sql")
                .contains("IN ({audiencePlaceholders})"));
    }

}
