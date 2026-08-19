package org.aincraft.guilds.territory.persist;

import java.sql.Connection;
import java.sql.SQLException;

/** Optional Java step that runs after a versioned SQL file is applied. */
@FunctionalInterface
public interface SqlMigrationHook {
    void after(Connection connection) throws SQLException;
}
