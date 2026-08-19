package org.aincraft.guilds.database;

import org.aincraft.guilds.database.migration.SchemaInitializer;
import org.aincraft.guilds.territory.persist.Database;
import org.aincraft.guilds.territory.persist.DatabaseFactory;
import org.aincraft.guilds.territory.persist.DatabaseSettingsLoader;
import org.aincraft.guilds.territory.persist.SqlSupport;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MySqlSchemaInitializerTest {
    @Test
    void initializesGuildsSchemaOnMysql() throws Exception {
        String url = System.getenv("GUILDS_TEST_MYSQL_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(),
                "Set GUILDS_TEST_MYSQL_JDBC_URL to run MySQL integration tests");
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.type", "mysql");
        cfg.put("database.jdbc-url", url);
        cfg.put("database.user", System.getenv().getOrDefault("GUILDS_TEST_MYSQL_USER", "guilds"));
        cfg.put("database.password", System.getenv().getOrDefault("GUILDS_TEST_MYSQL_PASSWORD", "guilds"));
        try (Database database = DatabaseFactory.open(DatabaseSettingsLoader.fromValues(cfg))) {
            JavaPlugin plugin = mock(JavaPlugin.class);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("mysql-schema"));
            try (Connection connection = database.connection()) {
                dropAllTables(connection);
            }
            database.initializeSchema();
            try (Connection connection = database.connection()) {
                new SchemaInitializer(plugin).initialize(connection);
                assertTrue(SqlSupport.tableExists(connection, "guilds"));
                assertTrue(SqlSupport.tableExists(connection, "residents"));
                assertTrue(SqlSupport.tableExists(connection, "alliances"));
                assertTrue(SqlSupport.tableExists(connection, "schema_migrations"));
            }
        }
    }

    private static void dropAllTables(Connection connection) throws Exception {
        List<String> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SHOW TABLES")) {
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : tables) {
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }
}
