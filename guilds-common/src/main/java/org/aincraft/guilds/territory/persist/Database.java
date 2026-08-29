package org.aincraft.guilds.territory.persist;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public interface Database extends AutoCloseable {
    DataSource dataSource();
    Connection connection() throws SQLException;
    void initializeSchema() throws IOException;
    DatabaseType type();
    DatabaseDialect dialect();
    /**
     * Returns live pool metrics when the underlying SQL owner exposes them.
     *
     * @return formatted pool metrics, or a stable unavailable message
     */
    default String poolStatistics() {
        return "Pool statistics not available";
    }
    @Override void close();
}
