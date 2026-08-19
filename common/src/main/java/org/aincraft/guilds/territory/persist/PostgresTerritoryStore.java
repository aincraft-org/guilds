package org.aincraft.guilds.territory.persist;

import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Concrete PostgreSQL store for the territory registry.
 *
 * <p>The connection pool is owned by {@link Database} and shared with
 * every other durable store.</p>
 */
public class PostgresTerritoryStore implements AutoCloseable {
    private final Database database;
    private final TerritoryJson json = new TerritoryJson();

    public PostgresTerritoryStore(Database database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    public void loadInto(TerritoryRegistry registry) throws IOException {
        List<Territory> territories = new ArrayList<>();
        try (Connection c = database.connection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT doc FROM territories ORDER BY id")) {
            while (rs.next()) {
                String doc = rs.getString("doc");
                territories.add(json.fromJson(JsonParser.parseString(doc).getAsJsonObject()));
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to load territories from PostgreSQL", e);
        }
        registry.replaceAll(territories);
    }

    public void save(TerritoryRegistry registry) throws IOException {
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try {
                try (Statement clear = c.createStatement()) {
                    clear.execute("DELETE FROM territories");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        database.dialect().documentUpsertSql("territories", "id"))) {
                    for (Territory t : registry.list()) {
                        ps.setString(1, t.id());
                        ps.setString(2, json.toJson(t).toString());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to save territories to PostgreSQL", e);
        }
    }

    @Override
    public void close() {
        // The shared Database owns the pool lifecycle.
    }
}
