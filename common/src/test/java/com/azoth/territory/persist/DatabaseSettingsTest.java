package com.azoth.territory.persist;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSettingsTest {
    @Test
    void defaultsToPostgres() {
        assertEquals(DatabaseType.POSTGRESQL, DatabaseSettings.defaults().type());
        assertTrue(DatabaseSettings.defaults().jdbcUrl().startsWith("jdbc:postgresql:"));
    }

    @Test
    void derivesMySqlUrlAndPort() {
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(Map.of("database.type", "mysql"));
        assertEquals(DatabaseType.MYSQL, s.type());
        assertEquals(3306, s.port());
        assertTrue(s.jdbcUrl().startsWith("jdbc:mysql://127.0.0.1:3306/azoth_territory"));
    }
    @Test
    void explicitMySqlPortIsUsed() {
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(Map.of(
                "database.type", "mysql", "database.port", 3306));
        assertEquals(3306, s.port());
        assertTrue(s.jdbcUrl().contains(":3306/"));
    }

    @Test
    void explicitSecureUrlWins() {
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(Map.of(
                "database.type", "mysql",
                "database.jdbc-url", "jdbc:mysql://example/db?sslMode=VERIFY_IDENTITY"));
        assertEquals("jdbc:mysql://example/db?sslMode=VERIFY_IDENTITY", s.jdbcUrl());
    }

    @Test
    void rejectsUnknownType() {
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseType.parse("sqlite"));
    }
}
