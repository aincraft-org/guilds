package org.aincraft.guilds.services;

import org.aincraft.guilds.territory.persist.SqlStatements;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlStatementsGuildsResourcesTest {
    @Test
    void loadsGuildInsertStatement() {
        String sql = SqlStatements.load("guilds/insert.sql");
        assertTrue(sql.contains("INSERT INTO guilds"), sql);
    }

    @Test
    void loadsEveryTravelStatementWithExpectedOperations() {
        List<String> names = List.of(
                "select-wallet.sql",
                "insert-wallet.sql",
                "update-wallet-balance.sql",
                "credit-wallet-balance.sql",
                "insert-award.sql",
                "insert-reservation.sql",
                "select-reservation.sql",
                "commit-reservation.sql",
                "release-reservation.sql",
                "select-expired-reservations.sql",
                "recover-reservation.sql");
        for (String name : names) {
            String sql = SqlStatements.load("travel/" + name);
            assertFalse(sql.isBlank(), name);
        }
        assertTrue(SqlStatements.load("travel/credit-wallet-balance.sql").contains("balance > ? - ?"));
        assertTrue(SqlStatements.load("travel/commit-reservation.sql").contains("status = 'RESERVED'"));
        assertTrue(SqlStatements.load("travel/release-reservation.sql").contains("status = 'RESERVED'"));
        assertTrue(SqlStatements.load("travel/update-wallet-balance.sql").contains("balance >= ?"));
        assertTrue(SqlStatements.load("travel/select-expired-reservations.sql")
                .contains("status = 'RESERVED' AND expires_at <= ?"));
        assertTrue(SqlStatements.load("travel/recover-reservation.sql").contains("status = 'RESERVED'"));
    }
}
