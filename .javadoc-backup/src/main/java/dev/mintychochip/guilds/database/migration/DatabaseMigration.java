package dev.mintychochip.guilds.database.migration;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface for database migrations
 * Each migration represents a schema change to be applied
 */
public interface DatabaseMigration {

    /**
     * Get the version number for this migration
     * @return Migration version (should be unique and sequential)
     */
    int getVersion();

    /**
     * Get a description of what this migration does
     * @return Migration description
     */
    String getDescription();

    /**
     * Apply this migration to the database
     * @param connection Database connection
     * @throws SQLException If migration fails
     */
    void migrate(Connection connection) throws SQLException;

    /**
     * Check if this migration has already been applied
     * @param connection Database connection
     * @return True if migration has been applied
     * @throws SQLException If check fails
     */
    boolean isApplied(Connection connection) throws SQLException;

    /**
     * Record that this migration has been applied
     * @param connection Database connection
     * @throws SQLException If recording fails
     */
    void markAsApplied(Connection connection) throws SQLException;
}