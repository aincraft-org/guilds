package org.aincraft.towny.database.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Town Toggle database migration
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Town Toggle Migration Tests")
class TownToggleMigrationTest {

    @Mock
    private Connection mockConnection;

    @Mock
    private Statement mockStatement;

    @Mock
    private ResultSet mockResultSet;

    private AddTownToggleMigration migration;

    @BeforeEach
    void setUp() throws SQLException {
        migration = new AddTownToggleMigration();
    }

    @Test
    @DisplayName("Should have correct migration version")
    void shouldHaveCorrectMigrationVersion() {
        assertThat(migration.getVersion()).isEqualTo(6);
    }

    @Test
    @DisplayName("Should have correct migration description")
    void shouldHaveCorrectMigrationDescription() {
        assertThat(migration.getDescription()).isEqualTo("Add town toggle system with dedicated boolean columns");
    }

    @Test
    @DisplayName("Should execute correct SQL statements during migration")
    void shouldExecuteCorrectSqlStatementsDuringMigration() throws SQLException {
        // Given
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        // When
        migration.migrate(mockConnection);

        // Then - Verify all ALTER TABLE statements are executed
        verify(mockStatement).execute("ALTER TABLE towns ADD COLUMN pvp_enabled BOOLEAN DEFAULT FALSE");
        verify(mockStatement).execute("ALTER TABLE towns ADD COLUMN fire_enabled BOOLEAN DEFAULT FALSE");
        verify(mockStatement).execute("ALTER TABLE towns ADD COLUMN explosions_enabled BOOLEAN DEFAULT FALSE");
        verify(mockStatement).execute("ALTER TABLE towns ADD COLUMN mobs_enabled BOOLEAN DEFAULT TRUE");
        verify(mockStatement).execute("ALTER TABLE towns ADD COLUMN public_enabled BOOLEAN DEFAULT FALSE");

        // Verify the UPDATE statement for default values
        verify(mockStatement).execute(contains("UPDATE towns"));
        verify(mockStatement).execute(contains("SET pvp_enabled = FALSE"));
        verify(mockStatement).execute(contains("SET fire_enabled = FALSE"));
        verify(mockStatement).execute(contains("SET explosions_enabled = FALSE"));
        verify(mockStatement).execute(contains("SET mobs_enabled = TRUE"));
        verify(mockStatement).execute(contains("SET public_enabled = FALSE"));
        verify(mockStatement).execute(contains("WHERE pvp_enabled IS NULL"));

        verify(mockConnection, times(6)).createStatement();
        verify(mockStatement, times(6)).close();
    }

    @Test
    @DisplayName("Should handle migration success correctly")
    void shouldHandleMigrationSuccessCorrectly() throws SQLException {
        // Given
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        // When & Then - Should not throw exception
        assertThatCode(() -> migration.migrate(mockConnection)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should propagate SQLException during migration")
    void shouldPropagateSQLExceptionDuringMigration() throws SQLException {
        // Given
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        doThrow(new SQLException("Database error")).when(mockStatement).execute(anyString());

        // When & Then
        assertThatThrownBy(() -> migration.migrate(mockConnection))
                .isInstanceOf(SQLException.class)
                .hasMessage("Database error");
    }

    @Test
    @DisplayName("Should properly close statements even on error")
    void shouldProperlyCloseStatementsEvenOnError() throws SQLException {
        // Given
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        doThrow(new SQLException("Database error")).when(mockStatement).execute(anyString());

        // When
        try {
            migration.migrate(mockConnection);
            fail("Expected SQLException");
        } catch (SQLException e) {
            // Expected
        }

        // Then - Statement should still be closed
        verify(mockStatement).close();
    }

    @Test
    @DisplayName("Should check if migration is applied correctly")
    void shouldCheckIfMigrationIsAppliedCorrectly() throws SQLException {
        // Given
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version = 6"))
                .thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getInt(1)).thenReturn(1);

        // When
        boolean isApplied = migration.isApplied(mockConnection);

        // Then
        assertThat(isApplied).isTrue();
        verify(mockStatement).executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version = 6");
        verify(mockResultSet).next();
        verify(mockResultSet).getInt(1);
    }

    @Test
    @DisplayName("Should return false when migration not applied")
    void shouldReturnFalseWhenMigrationNotApplied() throws SQLException {
        // Given
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version = 6"))
                .thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getInt(1)).thenReturn(0);

        // When
        boolean isApplied = migration.isApplied(mockConnection);

        // Then
        assertThat(isApplied).isFalse();
    }

    @Test
    @DisplayName("Should return false when schema_migrations table doesn't exist")
    void shouldReturnFalseWhenSchemaMigrationsTableDoesntExist() throws SQLException {
        // Given
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version = 6"))
                .thenThrow(new SQLException("Table doesn't exist"));

        // When
        boolean isApplied = migration.isApplied(mockConnection);

        // Then
        assertThat(isApplied).isFalse();
    }

    @Test
    @DisplayName("Should mark migration as applied correctly")
    void shouldMarkMigrationAsAppliedCorrectly() throws SQLException {
        // Given
        when(mockConnection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));

        // When
        migration.markAsApplied(mockConnection);

        // Then
        verify(mockConnection).prepareStatement("INSERT INTO schema_migrations (version, description, applied_at) VALUES (?, ?, ?)");
    }

    @Test
    @DisplayName("Should handle marking applied with SQL exception")
    void shouldHandleMarkingAppliedWithSQLException() throws SQLException {
        // Given
        when(mockConnection.prepareStatement(anyString()))
                .thenThrow(new SQLException("Failed to insert migration record"));

        // When & Then
        assertThatThrownBy(() -> migration.markAsApplied(mockConnection))
                .isInstanceOf(SQLException.class)
                .hasMessage("Failed to insert migration record");
    }

    @Test
    @DisplayName("Should have unique version number")
    void shouldHaveUniqueVersionNumber() {
        // Version 6 should be unique compared to other migrations
        assertThat(migration.getVersion()).isEqualTo(6);
        // This ensures it follows after AddTownLevelSystemMigration (v4)
        // and MigrateTownBlockToBitwiseMigration (v5)
    }

    @Test
    @DisplayName("Should follow naming convention")
    void shouldFollowNamingConvention() {
        assertThat(migration.getClass().getSimpleName()).isEqualTo("AddTownToggleMigration");
        assertThat(migration.getDescription()).contains("town toggle");
    }
}