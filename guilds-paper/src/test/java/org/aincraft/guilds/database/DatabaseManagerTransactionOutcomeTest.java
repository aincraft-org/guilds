package org.aincraft.guilds.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseManagerTransactionOutcomeTest {
    @Test
    void preservesCommittedOutcomeWhenConnectionCloseFails() throws Exception {
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("database-manager-test"));
        when(dataSource.getConnection()).thenReturn(connection);
        doNothing().when(connection).setAutoCommit(false);
        doNothing().when(connection).commit();
        doThrow(new SQLException("close failed")).when(connection).close();

        DatabaseManager databaseManager = new DatabaseManager(plugin, dataSource);
        String expected = "committed-value";
        DatabaseManager.TransactionExecutionResult<String> outcome =
                databaseManager.executeTransactionWithDetailedOutcome(ignored -> expected);

        assertEquals(DatabaseManager.TransactionCommitOutcome.COMMITTED, outcome.outcome());
        assertSame(expected, outcome.value());
        verify(connection).commit();
    }
}
