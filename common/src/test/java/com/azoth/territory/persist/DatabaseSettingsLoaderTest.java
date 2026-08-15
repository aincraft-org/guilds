package com.azoth.territory.persist;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSettingsLoaderTest {

    @Test
    void defaultsToDerivedUrl() {
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(new HashMap<>());
        assertEquals("jdbc:postgresql://127.0.0.1:5432/azoth_territory", s.jdbcUrl());
        assertEquals(10, s.poolSize());
    }

    @Test
    void readsEveryKey() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.host", "db.example.com");
        cfg.put("database.port", 5433);
        cfg.put("database.name", "azoth");
        cfg.put("database.user", "map");
        cfg.put("database.password", "hunter2");
        cfg.put("database.ssl", true);
        cfg.put("database.pool-size", 4);
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(cfg);
        assertEquals("db.example.com", s.host());
        assertEquals(5433, s.port());
        assertEquals("azoth", s.name());
        assertEquals("map", s.user());
        assertEquals("hunter2", s.password());
        assertTrue(s.ssl());
        assertEquals(4, s.poolSize());
        assertEquals("jdbc:postgresql://db.example.com:5433/azoth?sslmode=require", s.jdbcUrl());
    }

    @Test
    void rejectsPlaintextRemoteDatabase() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.host", "db.example.com");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> DatabaseSettingsLoader.fromValues(cfg));
        assertEquals("database.ssl must be true for non-loopback database hosts", ex.getMessage());
    }

    @Test
    void jdbcUrlOverrideWins() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.jdbc-url",
                "jdbc:postgresql://cloud.example.com:6543/azoth?sslmode=verify-full");
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(cfg);
        assertEquals("jdbc:postgresql://cloud.example.com:6543/azoth?sslmode=verify-full", s.jdbcUrl());
    }

    @Test
    void rejectsPlaintextRemoteJdbcOverride() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.jdbc-url",
                "jdbc:postgresql://db.example.com:5432/azoth?sslmode=disable");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> DatabaseSettingsLoader.fromValues(cfg));
        assertEquals("database.ssl must be true for non-loopback database hosts", ex.getMessage());
    }

    @Test
    void rejectsPlaintextRemoteMySqlOverride() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.type", "mysql");
        cfg.put("database.jdbc-url",
                "jdbc:mysql://db.example.com:3306/azoth?useSSL=false");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> DatabaseSettingsLoader.fromValues(cfg));
        assertEquals("database.ssl must be true for non-loopback database hosts", ex.getMessage());
    }

    @Test
    void rejectsInsecureOverrideEvenWhenSslFlagIsTrue() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.ssl", true);
        cfg.put("database.jdbc-url",
                "jdbc:postgresql://db.example.com:5432/azoth?sslmode=disable");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> DatabaseSettingsLoader.fromValues(cfg));
        assertEquals("database.ssl must be true for non-loopback database hosts", ex.getMessage());
    }

    @Test
    void acceptsVerifiedMySqlOverride() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.type", "mysql");
        cfg.put("database.jdbc-url",
                "jdbc:mysql://db.example.com:3306/azoth?sslMode=VERIFY_IDENTITY");

        DatabaseSettings settings = DatabaseSettingsLoader.fromValues(cfg);
        assertEquals("jdbc:mysql://db.example.com:3306/azoth?sslMode=VERIFY_IDENTITY",
                settings.jdbcUrl());
    }
}
