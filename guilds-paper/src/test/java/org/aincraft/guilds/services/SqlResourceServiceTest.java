package org.aincraft.guilds.services;

import org.aincraft.guilds.territory.persist.SqlStatements;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void resourcesSqlLivesOnTheClasspath() {
        assertTrue(SqlStatements.load("resources/insert.sql").toUpperCase(Locale.ROOT)
                .contains("INSERT INTO GUILD_RESOURCES"));
        assertTrue(SqlStatements.load("resources/insert-contribution.sql").toUpperCase(Locale.ROOT)
                .contains("INSERT INTO RESOURCE_CONTRIBUTIONS"));
    }

    @Test
    void levelsSqlLivesOnTheClasspath() {
        assertTrue(SqlStatements.load("levels/insert.sql").toUpperCase(Locale.ROOT)
                .contains("INSERT INTO GUILD_LEVELS"));
        assertTrue(SqlStatements.load("levels/insert-benefit.sql").toUpperCase(Locale.ROOT)
                .contains("INSERT INTO GUILD_LEVEL_BENEFITS"));
    }

    @Test
    void projectsSqlLivesOnTheClasspath() {
        assertTrue(SqlStatements.load("projects/insert.sql").toUpperCase(Locale.ROOT)
                .contains("INSERT INTO GUILD_UNLOCKED_NODES"));
        assertTrue(SqlStatements.load("projects/select-unlocked.sql").contains("WHERE guild_id = ?"));
    }

    @Test
    void contractsSqlLivesOnTheClasspath() {
        assertTrue(SqlStatements.load("contracts/insert.sql").toUpperCase(Locale.ROOT)
                .contains("INSERT INTO GUILD_CONTRACTS"));
        assertTrue(SqlStatements.load("contracts/select-by-id.sql").contains("WHERE id = ?"));
    }

    @Test
    void serviceImplsDoNotEmbedInsertOrCreateTable() throws Exception {
        Path dir = Path.of("src/main/java/org/aincraft/guilds/services/impl");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("ServiceImpl.java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                String name = file.getFileName().toString();
                assertFalse(source.contains("INSERT INTO"), name + " still embeds INSERT INTO");
                assertFalse(source.contains("CREATE TABLE"), name + " still embeds CREATE TABLE");
            }
        }
    }

}
