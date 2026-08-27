package dev.mintychochip.territory.persist;

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
    @Override void close();
}
