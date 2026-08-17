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

class PostgresTlsTest {
    @Test
    void connectsWithVerifyFullAndRootCertificate() throws IOException, SQLException {
        String host = env("AZOTH_TEST_POSTGRES_TLS_HOST");
        String port = env("AZOTH_TEST_POSTGRES_TLS_PORT");
        String caCert = env("AZOTH_TEST_POSTGRES_TLS_CA_CERT");
        Assumptions.assumeTrue(!host.isBlank(), "AZOTH_TEST_POSTGRES_TLS_* env not set; skipping live PostgreSQL TLS test");

        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.type", "postgresql");
        cfg.put("database.host", host);
        cfg.put("database.port", Integer.parseInt(port));
        cfg.put("database.name", "azoth_territory");
        cfg.put("database.user", "azoth");
        cfg.put("database.password", "azoth");
        cfg.put("database.ssl", true);
        cfg.put("database.ssl-mode", "verify-full");
        cfg.put("database.ssl-ca-cert", caCert);

        DatabaseSettings settings = DatabaseSettingsLoader.fromValues(cfg);
        assertEquals("verify-full", settings.sslMode());
        assertEquals(caCert, settings.dataSourceProperties().getProperty("sslrootcert"));

        try (Database db = DatabaseFactory.open(settings)) {
            try (Connection c = db.connection(); Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT 1")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    private String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
