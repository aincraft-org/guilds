package org.aincraft.guilds.territory.persist;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSettingsLoaderTest {

    @Test
    void defaultsToDerivedUrl() {
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(new HashMap<>());
        assertEquals("jdbc:postgresql://127.0.0.1:5432/azoth_territory", s.jdbcUrl());
        assertEquals("", s.sslMode());
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
        assertEquals("jdbc:postgresql://db.example.com:5433/azoth", s.jdbcUrl());
        assertEquals("verify-full", s.sslMode());
        Properties p = s.dataSourceProperties();
        assertEquals("verify-full", p.getProperty("sslmode"));
    }

    @Test
    void defaultsPostgresLoopbackSslToRequire() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.host", "127.0.0.1");
        cfg.put("database.ssl", true);
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(cfg);
        assertEquals("require", s.sslMode());
        assertEquals("require", s.dataSourceProperties().getProperty("sslmode"));
    }

    @Test
    void defaultsMySqlRemoteSslToVerifyIdentity() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.type", "mysql");
        cfg.put("database.host", "db.example.com");
        cfg.put("database.ssl", true);
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(cfg);
        assertEquals("jdbc:mysql://db.example.com:3306/azoth_territory", s.jdbcUrl());
        assertEquals("VERIFY_IDENTITY", s.sslMode());
        Properties p = s.dataSourceProperties();
        assertEquals("VERIFY_IDENTITY", p.getProperty("sslMode"));
        assertEquals("false", p.getProperty("allowPublicKeyRetrieval"));
        assertEquals("UTC", p.getProperty("serverTimezone"));
    }

    @Test
    void defaultsMySqlLoopbackSslToRequired() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.type", "mysql");
        cfg.put("database.host", "127.0.0.1");
        cfg.put("database.ssl", true);
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(cfg);
        assertEquals("REQUIRED", s.sslMode());
        Properties p = s.dataSourceProperties();
        assertEquals("REQUIRED", p.getProperty("sslMode"));
        assertEquals("false", p.getProperty("allowPublicKeyRetrieval"));
    }

    @Test
    void defaultsMySqlNoSslToDisabled() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.type", "mysql");
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(cfg);
        assertEquals("DISABLED", s.sslMode());
        Properties p = s.dataSourceProperties();
        assertEquals("DISABLED", p.getProperty("sslMode"));
        assertEquals("true", p.getProperty("allowPublicKeyRetrieval"));
    }

    @Test
    void postgresSslCaCertPassedAsRootCert() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.host", "db.example.com");
        cfg.put("database.ssl", true);
        cfg.put("database.ssl-ca-cert", "/etc/guilds/ca.crt");
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(cfg);
        assertEquals("/etc/guilds/ca.crt", s.sslCaCert());
        assertEquals("/etc/guilds/ca.crt", s.dataSourceProperties().getProperty("sslrootcert"));
    }

    @Test
    void mySqlTrustStorePassedAsDataSourceProperties() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.type", "mysql");
        cfg.put("database.host", "db.example.com");
        cfg.put("database.ssl", true);
        cfg.put("database.ssl-trust-store", "/etc/guilds/trust.p12");
        cfg.put("database.ssl-trust-store-password", "changeit");
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(cfg);
        Properties p = s.dataSourceProperties();
        assertEquals("file:/etc/guilds/trust.p12", p.getProperty("trustCertificateKeyStoreUrl"));
        assertEquals("PKCS12", p.getProperty("trustCertificateKeyStoreType"));
        assertEquals("changeit", p.getProperty("trustCertificateKeyStorePassword"));
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

    @Test
    void rejectsSslFalseWithTlsMode() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.host", "127.0.0.1");
        cfg.put("database.ssl", false);
        cfg.put("database.ssl-mode", "verify-full");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> DatabaseSettingsLoader.fromValues(cfg));
        assertTrue(ex.getMessage().contains("conflicts"));
    }

    @Test
    void rejectsSslTrueWithDisableMode() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.host", "db.example.com");
        cfg.put("database.ssl", true);
        cfg.put("database.ssl-mode", "disable");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> DatabaseSettingsLoader.fromValues(cfg));
        assertTrue(ex.getMessage().contains("conflicts"));
    }

    @Test
    void rejectsMySqlCaCert() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.type", "mysql");
        cfg.put("database.host", "db.example.com");
        cfg.put("database.ssl", true);
        cfg.put("database.ssl-ca-cert", "/etc/ca.crt");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> DatabaseSettingsLoader.fromValues(cfg));
        assertTrue(ex.getMessage().toLowerCase().contains("ssl-ca-cert"));
    }

    @Test
    void rejectsPostgresTrustStore() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.host", "db.example.com");
        cfg.put("database.ssl", true);
        cfg.put("database.ssl-trust-store", "/etc/trust.p12");
        cfg.put("database.ssl-trust-store-password", "changeit");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> DatabaseSettingsLoader.fromValues(cfg));
        assertTrue(ex.getMessage().toLowerCase().contains("ssl-trust-store"));
    }

    @Test
    void rejectsFallbackModesForRemotePostgres() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.host", "db.example.com");
        cfg.put("database.ssl", true);
        cfg.put("database.ssl-mode", "prefer");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> DatabaseSettingsLoader.fromValues(cfg));
        assertTrue(ex.getMessage().toLowerCase().contains("plaintext fallback"));
    }

    @Test
    void rejectsFallbackModeForRemoteMySql() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.type", "mysql");
        cfg.put("database.host", "db.example.com");
        cfg.put("database.ssl", true);
        cfg.put("database.ssl-mode", "PREFERRED");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> DatabaseSettingsLoader.fromValues(cfg));
        assertTrue(ex.getMessage().toLowerCase().contains("plaintext fallback"));
    }

    @Test
    void expandsLoopbackRange() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.host", "127.54.3.2");
        DatabaseSettings s = DatabaseSettingsLoader.fromValues(cfg);
        assertTrue(s.jdbcUrl().contains("127.54.3.2"));
    }
}
