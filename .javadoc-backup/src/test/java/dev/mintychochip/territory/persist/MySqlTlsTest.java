package dev.mintychochip.territory.persist;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MySqlTlsTest {

    @Test
    void connectsWithVerifyIdentity() throws IOException, SQLException {
        String host = env("AZOTH_TEST_MYSQL_TLS_HOST");
        String port = env("AZOTH_TEST_MYSQL_TLS_PORT");
        String trustStore = env("AZOTH_TEST_MYSQL_TLS_TRUST_STORE");
        String trustStorePassword = env("AZOTH_TEST_MYSQL_TLS_TRUST_STORE_PASSWORD");
        Assumptions.assumeTrue(!host.isBlank(), "AZOTH_TEST_MYSQL_TLS_* env not set; skipping live MySQL TLS test");

        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.type", "mysql");
        cfg.put("database.host", host);
        cfg.put("database.port", Integer.parseInt(port));
        cfg.put("database.name", "azoth_territory");
        cfg.put("database.user", "azoth");
        cfg.put("database.password", "azoth");
        cfg.put("database.ssl", true);
        cfg.put("database.ssl-mode", "VERIFY_IDENTITY");
        cfg.put("database.ssl-trust-store", trustStore);
        cfg.put("database.ssl-trust-store-password", trustStorePassword);

        DatabaseSettings settings = DatabaseSettingsLoader.fromValues(cfg);
        assertEquals("VERIFY_IDENTITY", settings.sslMode());
        assertEquals("file:" + trustStore, settings.dataSourceProperties().getProperty("trustCertificateKeyStoreUrl"));

        try (Database db = DatabaseFactory.open(settings)) {
            try (Connection c = db.connection(); Statement s = c.createStatement()) {
                try (ResultSet rs = s.executeQuery("SELECT 1")) {
                    rs.next();
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }

    private String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
