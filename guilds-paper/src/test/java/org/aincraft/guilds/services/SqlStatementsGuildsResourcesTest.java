package org.aincraft.guilds.services;

import org.aincraft.guilds.territory.persist.SqlStatements;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlStatementsGuildsResourcesTest {
    @Test
    void loadsGuildInsertStatement() {
        String sql = SqlStatements.load("guilds/insert.sql");
        assertTrue(sql.contains("INSERT INTO guilds"), sql);
    }
}
